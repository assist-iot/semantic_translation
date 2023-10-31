package eu.assist_iot.ipsm.core.datamodel

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import io.getquill._

import scala.concurrent.duration.DurationInt

object IPSMModel {
  type DbContext =  SqliteJdbcContext[CamelCase.type]
  lazy val ctx: DbContext = new SqliteJdbcContext(CamelCase,"ipsm.db-sqlite")

  def createDbConnection: Future[DbContext] = {
    val result = Future { ctx }
    Await.ready(result, 10.seconds)
  }
}
