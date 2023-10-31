package eu.assist_iot.ipsm.core.rest.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, entity, onComplete, path}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.channels.ChannelManager
import eu.assist_iot.ipsm.core.datamodel._
import eu.assist_iot.ipsm.core.rest.{JsonSupport, XMLSupport}
import spray.json.DefaultJsonProtocol._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


trait ChannelRoutes extends JsonSupport with XMLSupport with LazyLogging {

  private def uuid(): String = {
    java.util.UUID.randomUUID.toString
  }

  implicit val system: ActorSystem
  implicit val executionContext: ExecutionContext

  def channelRoutes: Route = {
    path("channels") {
      get {
        listChannels()
      } ~
        post {
          entity(as[ChannelConfig]) { conf =>
            createChannel(conf)
          }
        }
    } ~
      path("channels" / IntNumber) { id =>
        delete {
          deleteChannel(id)
        }
      }
  }

  private def createChannel(chanConf: ChannelConfig): Route = {

    //TODO: do we really need to impose the restriction below?
    if (ChannelManager.checkIfExists(chanConf.sink)) {
      complete(StatusCodes.BadRequest, ErrorResponse(
        s"Channel with output ID=“${chanConf.sink}” already exists."
      ))
    }

    // For KK-type channels, if not provided the Kafka consumer group for the channel gets initialized to a fresh UUID
    val conf = if (chanConf.chanType == "KK") {
      if (chanConf.optConsumerGroup.isEmpty) {
        chanConf.copy(optConsumerGroup = Some(uuid()))
      } else {
        chanConf
      }
    } else {
      chanConf.copy(optConsumerGroup = None)
    }

    val chanUUID = uuid()

    val saved: Future[Materializer] = ChannelManager.materializeChannel(chanUUID, conf, system)

    onComplete(saved) {
      case Success(done) =>
        val chan = ChannelManager.createChannel(done, conf, chanUUID)
        complete(
          StatusCodes.Created,
          SuccessResponse(message = s"Channel ${chan.id} of type “${conf.chanType}“ created successfully.")
        )
      case Failure(exc) =>
        complete(
          StatusCodes.InternalServerError,
          ErrorResponse(s"Channel creation failed: ${exc.getMessage}")
        )
    }
  }

  private def deleteChannel(id: Int): Route = ChannelManager.channels.get(id) match {
    case Some((_, _)) =>
      val res = ChannelManager.deleteChannel(id)
      if (res) {
        complete(
          StatusCodes.OK,
          SuccessResponse(s"Channel $id successfully closed.")
        )
      } else {
        complete(
          StatusCodes.InternalServerError,
          ErrorResponse(s"Closing channel “$id” FAILED.")
        )
      }
    case None =>
      complete(
        StatusCodes.BadRequest,
        ErrorResponse(s"Channel with identifier “$id” not found, already deleted?")
      )
  }

  private def listChannels(): Route = {
    complete(
      StatusCodes.OK,
      for (p <- ChannelManager.channels.toList) yield {
        val tc = p._2._2
        val inpAlName = tc.inpAlignmentName
        val inpAlSpec = if (inpAlName.isEmpty) "{identity}" else s"{$inpAlName}:${tc.inpAlignmentVersion}"
        val outAlName = tc.outAlignmentName
        val outAlSpec = if (outAlName.isEmpty) "{identity}" else s"{$outAlName}:${tc.outAlignmentVersion}"
        ChannelInfo(
          p._1,
          tc.chanType,
          tc.source,
          tc.inpAlignmentName,
          tc.inpAlignmentVersion,
          tc.outAlignmentName,
          tc.outAlignmentVersion,
          tc.sink,
          tc.consumerGroup,
          tc.parallelism,
          tc.uuid,
          s"${tc.source} ⇛ ⟪ $inpAlSpec ⇒ $outAlSpec ⟫ ⇛ ${tc.sink}"
        )
      }
    )
  }

}
