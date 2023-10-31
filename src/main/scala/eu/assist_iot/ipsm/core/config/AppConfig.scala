package eu.assist_iot.ipsm.core.config

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object AppConfig extends LazyLogging {
  private val config: Config = ConfigFactory.load()

  // Supported IPSM broker types
  private val channelTypesTry: Try[Set[String]] =
    Try(config.getStringList(
      "ipsm.supported-channel-types"
    ).asScala.toSet[String].map(s => s.toUpperCase()).intersect(Set("MM", "KK")))
  // IPSM REST interface configuratopn
  private val httpHostTry: Try[String] = Try(config.getString("ipsm.http.host"))
  private val httpPortTry: Try[Int] = Try(config.getInt("ipsm.http.port"))
  // MQTT broker(s) configuration
  private val mqttMessageSizeLimitInKBTry: Try[Int] = Try(config.getInt("ipsm.mqtt.messageSizeLimitInKB"))
  private val mqttSrcHostTry: Try[String] = Try(config.getString("ipsm.mqtt.source.host"))
  private val mqttSrcPortTry: Try[Int] = Try(config.getInt("ipsm.mqtt.source.port"))
  private val mqttTrgHostTry: Try[String] = Try(config.getString("ipsm.mqtt.target.host"))
  private val mqttTrgPortTry: Try[Int] = Try(config.getInt("ipsm.mqtt.target.port"))
  // Kafka configuration
  private val kafkaHostTry: Try[String] = Try(config.getString("ipsm.kafka.host"))
  private val kafkaPortTry: Try[Int] = Try(config.getInt("ipsm.kafka.port"))

  private def verifyHostPortData(hostTry: Try[String], portTry: Try[Int], msg: String): Boolean = {
    val spec = hostTry.isSuccess && portTry.isSuccess
    if (!spec) {
      logger.error(s"HOST/PORT data $msg not specified.")
    }
    spec
  }

  logger.info(s"mqttSrcHostTry = $mqttSrcHostTry; mqttSrcPortTry = $mqttSrcPortTry;")
  logger.info(s"mqttTrgHostTry = $mqttTrgHostTry; mqttTrgPortTry = $mqttTrgPortTry")

  def valid(): Boolean = {
    val typesAndBrokerHostsDataOk = channelTypesTry match {
      case Success(types) =>
        val tests = for {
          chT <- types.toList
        } yield chT match {
          case "MM" =>
            verifyHostPortData(mqttSrcHostTry, mqttSrcPortTry, "for the MQTT source broker") &&
            verifyHostPortData(mqttTrgHostTry, mqttTrgPortTry, "for the MQTT target broker")
          case "KK" =>
            verifyHostPortData(kafkaHostTry, kafkaPortTry, "KK")
          case _ =>
            logger.error(s"Unhandled broker type “$chT” specified.")
            false
        }
        tests.forall(identity)
      case Failure(exc) =>
        logger.error(s"Allowed channel types not specified in the configuration: ${exc.getMessage}")
        false
    }
    typesAndBrokerHostsDataOk &&
      verifyHostPortData(httpHostTry, httpPortTry, "for the IPSM REST service")
  }

  lazy val channelTypes: Set[String] = channelTypesTry.getOrElse(Set.empty)
  lazy val httpHost: String = httpHostTry.getOrElse("")
  lazy val httpPort: Int = httpPortTry.getOrElse(-1)
  lazy val mqttSrcHost: String = mqttSrcHostTry.getOrElse("")
  lazy val mqttSrcPort: Int = mqttSrcPortTry.getOrElse(-1)
  lazy val mqttTrgHost: String = mqttTrgHostTry.getOrElse("")
  lazy val mqttTrgPort: Int = mqttTrgPortTry.getOrElse(-1)
  lazy val kafkaHost: String = kafkaHostTry.getOrElse("")
  lazy val kafkaPort: Int = kafkaPortTry.getOrElse(-1)
  lazy val mqttMessageSizeLimitInKB: Int = mqttMessageSizeLimitInKBTry.getOrElse(256)

}
