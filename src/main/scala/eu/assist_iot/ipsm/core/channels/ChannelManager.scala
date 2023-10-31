package eu.assist_iot.ipsm.core.channels

import java.util.Calendar
import akka.NotUsed
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.kafka.scaladsl.{Committer, Producer}
import akka.kafka.{CommitterSettings, ProducerMessage, ProducerSettings}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorAttributes, Attributes, Materializer, OverflowStrategy, Supervision}
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.alignments.{Alignment, AlignmentsManager}
import eu.assist_iot.ipsm.core.config.{AppConfig, KafkaBootstrap}
import eu.assist_iot.ipsm.core.datamodel._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import pl.waw.ibspan.scala_mqtt_wrapper.akka._
import akka.stream.alpakka.mqtt.streaming.{ControlPacketFlags, MqttSessionSettings}
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

case class ChanMatConf(inpOpt: Option[Alignment], outOpt: Option[Alignment], parallelism: Int)

object ChannelManager extends ChannelManagementOps with LazyLogging {

  import akka.kafka.scaladsl.Consumer
  import akka.kafka.{ConsumerSettings, Subscriptions}
  import org.apache.kafka.clients.consumer.ConsumerConfig
  import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

  def materializeChannel(uuid: String, conf: ChannelConfig, system: ClassicActorSystem): Future[Materializer] = {
    val inpAlignmentOpt = AlignmentsManager.inputAlignment(conf)
    val outAlignmentOpt = AlignmentsManager.outputAlignment(conf)
    val parallelism = conf.optParallelism.getOrElse(1).max(1)
    val chanMatConf = ChanMatConf(inpAlignmentOpt, outAlignmentOpt, parallelism)

    conf.chanType match {
      case chT if !AppConfig.channelTypes.contains(chT) =>
        Future(throw new IllegalArgumentException(s"The “$chT” channel type is not supported by the deployment."))
      case "MM" =>
        ChannelManager.materializeMqttChannel(uuid, conf, system, chanMatConf)
      case "KK" =>
        ChannelManager.materializeKafkaChannel(uuid, conf, system, chanMatConf)
      case t =>
        Future(throw new IllegalArgumentException(s"Unknown channel type “$t”."))
    }
  }

  //-------------------------------------------------------------------------------------------------
  // Kafka-to-Kafka channels
  //-------------------------------------------------------------------------------------------------
  private lazy val kafkaBootstrap: String = {
    //noinspection FieldFromDelayedInit
    val bs = KafkaBootstrap.config
    val hostname = s"${bs.hostname}:${bs.port}"
    logger.info(s"Kafka server bootstrap: $hostname")
    hostname
  }

  private def materializeKafkaChannel(
    chanUUID: String,
    chanConf: ChannelConfig,
    actorSystem: ClassicActorSystem,
    chanMatConf: ChanMatConf
  ): Future[Materializer] = Future {
    // Create a "local" materializer for the channel.
    // It will stay alive as long as the actorSystem does or until it is explicitly stopped
    val channelMat = Materializer(actorSystem)

    val producerSettings = ProducerSettings(actorSystem, new ByteArraySerializer, new StringSerializer)
      .withBootstrapServers(kafkaBootstrap)

    val producer = producerSettings.createKafkaProducer()

    val consumerSettings = ConsumerSettings(actorSystem, new ByteArrayDeserializer, new StringDeserializer)
      .withBootstrapServers(kafkaBootstrap)
      .withGroupId(chanConf.optConsumerGroup.get)
      .withProperty("dual.commit.enabled", "false")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

    val committerSettings = CommitterSettings(actorSystem).withParallelism(chanMatConf.parallelism)

    Consumer.committableSource(consumerSettings, Subscriptions.topics(chanConf.source))
      .buffer(1000, OverflowStrategy.backpressure)
      .mapAsync(chanMatConf.parallelism)(msg => Future {
        val rec = msg.record
        val startTime = Calendar.getInstance.getTimeInMillis
        logger.debug(s"topic: ${rec.topic()}, partition: ${rec.partition()}, value: ${rec.value()}, offset: ${rec.offset()}")
        val message = new Message(msg.record.value())
        Try(message.getPayload) match {
          case Success(inpGraph) =>
            val outGraph = (chanMatConf.inpOpt, chanMatConf.outOpt) match {
              case (None, None) =>
                logger.trace("Both input and output are IDENTITY alignments – graph unchanged.")
                inpGraph
              case (None, Some(outA)) =>
                logger.trace("IDENTITY input alignment - applying only output alignment.")
                outA.execute(inpGraph)
              case (Some(inpA), None) =>
                logger.trace("IDENTITY output alignment - applying only input alignment.")
                inpA.execute(inpGraph)
              case (Some(inpA), Some(outA)) =>
                logger.trace("Applying both input and output alignments.")
                outA.execute(inpA.execute(inpGraph))
            }
            (Some(outGraph), message, msg.committableOffset, startTime)
          case Failure(exc) => exc match {
            case _:EmptyPayloadException =>
              logger.trace("Message with empty payload received")
              (None, message, msg.committableOffset, startTime)
            case _ =>
              logger.error(s"Exception extracting message payload: ${exc.getMessage}")
              throw exc
          }
        }
      })
      .mapAsync(chanMatConf.parallelism)( out => Future {
        val (modelOpt, message, offset, startTime) = out
        if (modelOpt.isDefined) {
          logger.trace("Setting the translated graph as the payload")
          message.setPayload(modelOpt.get)
        }
        logger.debug(s"Returning translated message to Kafka: ${message.serialize}")
        ProducerMessage.Message(new ProducerRecord[Array[Byte], String](
          chanConf.sink,
          message.serialize
        ), (offset, startTime))
      })
      .via(Producer.flexiFlow(producerSettings))
      .alsoTo(Sink.foreach { msg =>
        val (_, startTime) = msg.passThrough
        producer.send(new ProducerRecord(
          s"mon-$chanUUID",
          0,
          null: Array[Byte], // scalastyle:ignore
          startTime.toString + ";" + Calendar.getInstance.getTimeInMillis.toString
        ))
      })
      .map(_.passThrough._1)
      .runWith(Committer.sink(committerSettings))(channelMat)
    channelMat
  }

  //-------------------------------------------------------------------------------------------------
  // MQTT-to-MQTT channels
  //-------------------------------------------------------------------------------------------------

  private def materializeMqttChannel(
    chanUUID: String,
    chanConf: ChannelConfig,
    actorSystem: ClassicActorSystem,
    chanMatConf: ChanMatConf
  ): Future[Materializer] = Future {
    // Create a "local" materializer for the channel.
    // It will stay alive as long as the actorSystem does or until it is explicitly stopped
    val channelMat = Materializer(actorSystem)
    implicit val typedSystem: ActorSystem[Nothing] = actorSystem.toTyped

    // create shared logging attributes
//    val loggingAttributes = Attributes.logLevels(
//      onElement = Attributes.LogLevels.Debug,
//      onFinish = Attributes.LogLevels.Debug,
//      onFailure = Attributes.LogLevels.Debug,
//    )

    lazy val loggingAttributes = Attributes.logLevels(
      onElement = Attributes.LogLevels.Error,
      onFinish = Attributes.LogLevels.Error,
      onFailure = Attributes.LogLevels.Error,
    )

    // create a client connected to an MQTT broker and subscribe to one topic ("input")
    def mqttSource(): Source[MqttReceivedMessage, NotUsed] = {
      // create a client connected to an MQTT broker and subscribe to one topic ("input")
      val topic = chanConf.source
      val sourceClient = new MqttClient(
        MqttSettings(
          host = AppConfig.mqttSrcHost,
          port = AppConfig.mqttSrcPort,
          subscriptions = Seq(MqttTopic(name = topic, flags = SubscribeQoSFlags.QoSAtLeastOnceDelivery))
        ),
        MqttSessionSettings()
          .withMaxPacketSize(256 * 1024), // 256 KB
        loggingSettings = Some(
          MqttLoggingSettings(name = s"${topic}SrcClient", attributes = loggingAttributes)
        )
      )
      // create a source emitting messages from the subscribed topic
      MqttSource.source(
        sourceClient,
        loggingSettings = Some(MqttLoggingSettings(name = topic, attributes = loggingAttributes))
      )
    }

    // create a client connected to the same MQTT broker and sink to publish messages
    def mqttSink(): Sink[MqttPublishMessage, NotUsed] = {
      // create a client connected to the same MQTT broker
      val topic = chanConf.sink
      val sinkClient = new MqttClient(
        MqttSettings(
          host = AppConfig.mqttTrgHost,
          port = AppConfig.mqttTrgPort
        ),
        loggingSettings = Some(
          MqttLoggingSettings(name = s"${topic}TrgClient", attributes = loggingAttributes)
        )
      )
      // create a sink to publish messages
      MqttSink.sink(
        sinkClient,
        loggingSettings = Some(MqttLoggingSettings(name = topic, attributes = loggingAttributes))
      )
    }

    def mqttFlow(): Flow[MqttReceivedMessage, MqttPublishMessage, NotUsed] = {
      val parallelism = chanConf.optParallelism.getOrElse(1).max(1)
      Flow[MqttReceivedMessage].mapAsync(parallelism) {
        case MqttReceivedMessage(payload, _, _, _) => Future {
          val startTime = Calendar.getInstance.getTimeInMillis
          val payloadAsUtf8String = payload.utf8String
          val message = new Message(payloadAsUtf8String)
          Try(message.getPayload) match {
            case Success(inpGraph) =>
              val outGraph = (chanMatConf.inpOpt, chanMatConf.outOpt) match {
                case (None, None) =>
                  logger.trace("Both input and output are IDENTITY alignments - graph unchanged")
                  inpGraph
                case (None, Some(outA)) =>
                  logger.trace("IDENTITY input alignment - applying only output alignment")
                  outA.execute(inpGraph)
                case (Some(inpA), None) =>
                  logger.trace("IDENTITY output alignment - applying only input alignment")
                  inpA.execute(inpGraph)
                case (Some(inpA), Some(outA)) =>
                  logger.trace("Applying both input and output alignment")
                  outA.execute(inpA.execute(inpGraph))
              }
              (Some(outGraph), message, startTime)
            case Failure(exc) => exc match {
              case _: EmptyPayloadException =>
                logger.trace("Message with empty payload received")
                (None, message, startTime)
              case _ =>
                logger.error(s"Exception extracting message payload: ${exc.getMessage}")
                throw exc
            }
          }
        }
      }.mapAsync(parallelism) {
        case (modelOpt, message, startTime) => Future {
          if (modelOpt.isDefined) {
            logger.trace("Setting the translated graph as the payload")
            message.setPayload(modelOpt.get)
          }
          logger.debug(s"Returning translated message to MQTT: ${message.serialize}")
          val publishFlags = ControlPacketFlags.QoSAtLeastOnceDelivery | ControlPacketFlags.RETAIN
          val result = ByteString(message.serialize)
          MqttPublishMessage(result, chanConf.sink, publishFlags)
        }
      }
    }

    val decider: Supervision.Decider = {
      case exc: Exception =>
        logger.error(s"Caught exception: ${exc.getMessage}")
        Supervision.Resume
    }

    val runnableGraph = mqttSource()
      .buffer(1000, OverflowStrategy.backpressure)
      .via(mqttFlow())
      .log("sem-trans-stream-log")

    val withCustomSupervision =
      runnableGraph.withAttributes(ActorAttributes.supervisionStrategy(decider))

    withCustomSupervision
      .runWith(mqttSink())(channelMat)
    channelMat
  }

}
