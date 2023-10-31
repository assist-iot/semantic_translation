package eu.assist_iot.ipsm.core.rest

import akka.actor.ActorSystem
import akka.http.javadsl.model.HttpMethods._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import eu.assist_iot.ipsm.core.Reaper.AllDone
import eu.assist_iot.ipsm.core.datamodel._
import eu.assist_iot.ipsm.core.rest.routes.{AdminRoutes, AlignmentRoutes, ApiExportRoutes, ChannelRoutes, InfoRoutes, LogRoutes}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait RestApi extends ApiExportRoutes
  with AlignmentRoutes
  with ChannelRoutes
  with InfoRoutes
  with LogRoutes
  with AdminRoutes
  with JsonSupport
  with XMLSupport {

  private val corsSettings = CorsSettings
    .defaultSettings
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(GET, POST, DELETE, PUT, HEAD, OPTIONS).asJava)

  val api: Route =
    cors(corsSettings) {
      pathPrefix("v1") {
        concat(
          pathPrefix("api-export") {
            apiExportRoutes
          },
          channelRoutes,
          alignmentRoutes,
          infoRoutes,
          loggingRoutes,
          adminRoutes
        )
      } ~
        infoRoutes ~
        loggingRoutes ~
        adminRoutes
    }
}
