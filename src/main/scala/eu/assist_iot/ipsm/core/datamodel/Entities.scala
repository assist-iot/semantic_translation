package eu.assist_iot.ipsm.core.datamodel

import eu.assist_iot.ipsm.core.channels.ChannelType

import java.time.Instant


final case class ChannelConfig(
  chanType: ChannelType,
  source: String,
  inpAlignmentName: String,
  inpAlignmentVersion: String,
  outAlignmentName: String,
  outAlignmentVersion: String,
  sink: String,
  optConsumerGroup: Option[String] = None,
  optParallelism: Option[Int] = None
) {
  def inpAlignmentId: AlignmentID = AlignmentID(inpAlignmentName, inpAlignmentVersion)
  def outAlignmentId: AlignmentID = AlignmentID(outAlignmentName, outAlignmentVersion)
}

final case class Channel( // ChannelData
  id: Int,
  chanType: ChannelType,
  source: String,
  inpAlignmentName: String,
  inpAlignmentVersion: String,
  outAlignmentName: String,
  outAlignmentVersion: String,
  sink: String,
  consumerGroup: String,
  parallelism: Int,
  uuid: String
)

final case class ChannelInfo(
  id: Int,
  chanType: ChannelType,
  source: String,
  inpAlignmentName: String,
  inpAlignmentVersion: String,
  outAlignmentName: String,
  outAlignmentVersion: String,
  sink: String,
  consumerGroup: String,
  parallelism: Int,
  uuid: String,
  descId: String
)

final case class SuccessResponse(message: String)
final case class ErrorResponse(message: String)
final case class LoggingResponse(message: String, level: String)
final case class InfoResponse(info: String, value: String)
final case class FullVersionResponse(name: String, version: String)
final case class VersionResponse(version: String)

final case class AlignmentConfig(
  name: String,
  sourceOntologyURI: String,
  targetOntologyURI: String,
  version: String,
  creator: String,
  description: String,
  xmlSource: String
) {
  def alignmentID: AlignmentID = AlignmentID(name, version)
}

final case class AlignmentData(
  id: Int,
  date: Instant,
  name: String,
  sourceOntologyURI: String,
  targetOntologyURI: String,
  version: String,
  creator: String,
  description: String,
  xmlSource: String
) {
  def alignmentID: AlignmentID = AlignmentID(name, version)
}

final case class AlignmentInfo(
  id: Int,
  date: Instant,
  name: String,
  sourceOntologyURI: String,
  targetOntologyURI: String,
  version: String,
  creator: String,
  description: String,
  descId: String
)

final case class TranslationData(alignIDs: List[AlignmentID], graphStr: String)
final case class TranslationResponse(message: String, graphStr: String)

