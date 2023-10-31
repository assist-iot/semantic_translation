package eu.assist_iot.ipsm.core.rest

import eu.assist_iot.ipsm.core.datamodel._
import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

import java.sql.Timestamp

object IPSMJsonProtocol extends DefaultJsonProtocol {
  implicit object TimestampFormat extends JsonFormat[Timestamp] {
    def write(obj: Timestamp): JsValue = JsNumber(obj.getTime)

    def read(json: JsValue): Timestamp = json match {
      case JsNumber(time) => new Timestamp(time.toLong)
      case _ => throw DeserializationException("Date expected")
    }
  }
}

trait JsonSupport {

  import IPSMJsonProtocol._

  import java.time.Instant
  implicit val errorFormat: RootJsonFormat[ErrorResponse] = jsonFormat1(ErrorResponse.apply)
  implicit val instantFormat: RootJsonFormat[Instant] = new RootJsonFormat[Instant] {
    override def read(jsValue: JsValue): Instant = jsValue match {
      case JsString(time) => Instant.parse(time)
      case _ => deserializationError("Instant required as a string of RFC3339 UTC Zulu format.")
    }
    override def write(instant: Instant): JsValue = JsString(instant.toString)
  }
  implicit val responseFormat: RootJsonFormat[SuccessResponse] = jsonFormat1(SuccessResponse.apply)
  implicit val versionFormnat: RootJsonFormat[VersionResponse] = jsonFormat1(VersionResponse.apply)
  implicit val fullVersionResponseFormat: RootJsonFormat[FullVersionResponse] = jsonFormat2(FullVersionResponse.apply)
  implicit val loggingResponseFormat: RootJsonFormat[LoggingResponse] = jsonFormat2(LoggingResponse.apply)
  implicit val versionResponseFormat: RootJsonFormat[InfoResponse] = jsonFormat2(InfoResponse.apply)
  implicit val alignmentInfoFormat: RootJsonFormat[AlignmentInfo] = jsonFormat9 (AlignmentInfo.apply)
  implicit val channelConfigFormat: RootJsonFormat[ChannelConfig] = jsonFormat9(ChannelConfig.apply)
  implicit val channelInfoFormat: RootJsonFormat[ChannelInfo] = jsonFormat12(ChannelInfo.apply)
  implicit val alignemntIDFormat: RootJsonFormat[AlignmentID] = jsonFormat2(AlignmentID.apply)
  implicit val translationDataFormat: RootJsonFormat[TranslationData] = jsonFormat2(TranslationData.apply)
  implicit val translationResponseFormat: RootJsonFormat[TranslationResponse] = jsonFormat2(TranslationResponse.apply)

}
