package eu.assist_iot.ipsm.core
package rest.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.Reaper.AllDone
import eu.assist_iot.ipsm.core.rest.{JsonSupport, XMLSupport}
import eu.assist_iot.ipsm.core.datamodel._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait AdminRoutes extends JsonSupport with XMLSupport with LazyLogging {
  implicit val system: ActorSystem
  implicit val executionContext: ExecutionContext
  implicit val timeout: Timeout = Timeout(10.seconds)

  def adminRoutes: Route = {
    path("terminate") {
      for (reaper <- system.actorSelection("/user/reaper").resolveOne()) {
        reaper ! AllDone
      }
      logger.info("Initiating SemTrans shutdown procedure")
      complete(StatusCodes.OK, SuccessResponse("SemTrans is shutting down"))
    }
  }

}
