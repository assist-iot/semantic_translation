package eu.assist_iot.ipsm.core.config

import com.typesafe.config.ConfigFactory
import org.rogach.scallop.{ArgType, ValueConverter}

import scala.util.matching.Regex

object KafkaBootstrap {
  val config: KafkaBootstrap = KafkaBootstrap(AppConfig.kafkaHost, AppConfig.kafkaPort)
  val hostnameConverter: ValueConverter[KafkaBootstrap] = new ValueConverter[KafkaBootstrap] {
    val nameRgx:Regex = """([a-z0-9.]+):(\d+)""".r
    // parse is a method, that takes a list of arguments to all option invokations:
    // for example, "-a 1 2 -a 3 4 5" would produce List(List(1,2),List(3,4,5)).
    // parse returns Left with error message, if there was an error while parsing
    // if no option was found, it returns Right(None)
    // and if option was found, it returns Right(...)
    def parse(s:List[(String, List[String])]): Either[String, Option[KafkaBootstrap]] = s match {
      case (_, nameRgx(hname, port) :: Nil) :: Nil =>
        Right(Some(KafkaBootstrap(hname, port.toInt)))
      case Nil =>
        Right(None)
      case _ =>
        Left("Provide a valid Kafka server hostname and port")
    }

    // some "magic" to make typing work
    // val tag: universe.TypeTag[KafkaBootstrap] = reflect.runtime.universe.typeTag[KafkaBootstrap]
    val argType: ArgType.V = org.rogach.scallop.ArgType.SINGLE
  }
}

case class KafkaBootstrap(hostname: String, port: Int) {
  override def toString: String = s"$hostname:$port"
}
