package eu.assist_iot.ipsm.core
package rest.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core._
import eu.assist_iot.ipsm.core.rest.{JsonSupport, XMLSupport}
import zio.BuildInfo

trait InfoRoutes extends JsonSupport with XMLSupport with LazyLogging {

  def infoRoutes: Route = {
    path("version" ~ Slash.?) {
      get {
        getVersionRoute
      }
    } ~
    path("info" ~ Slash.?) {
      get {
        getInfoRoute
      }
    } ~
    path("health" ~ Slash.?) {
      get {
        getHealthRoute
      }
    }
  }

  private def getInfoRoute: Route = pathEndOrSingleSlash {
    complete(
      StatusCodes.OK,
      datamodel.FullVersionResponse("SemTrans", BuildInfo.version)
    )
  }

  private def getVersionRoute: Route = pathEndOrSingleSlash {
    complete(
      StatusCodes.OK,
      datamodel.VersionResponse(BuildInfo.version)
    )
  }

  private def getHealthRoute: Route = pathEndOrSingleSlash {
    complete(
      StatusCodes.OK,
      datamodel.InfoResponse("healthStatus", "Still alive…")
    )
  }

}
