package eu.assist_iot.ipsm.core
package datamodel

import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.{JsonLDWriteContext, Lang, RDFDataMgr, RDFFormat, RDFWriter}

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.github.jsonldjava.core.JsonLdOptions
import com.typesafe.scalalogging.LazyLogging

import scala.annotation.unused

case class EmptyPayloadException(private val message: String = "", private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

class Message(jsonLdStr: String) extends LazyLogging {

  private val URIBaseINTERIoT = "http://inter-iot.eu/"
  private val URIBaseMessage: String = URIBaseINTERIoT + "message/"
  private val URImessagePayloadGraph = URIBaseMessage + "payload"
  private var withPayload = true

  private val messageDataset = readDatasetFromJSONLD(jsonLdStr)

  def getPayload: Model = {
    if (messageDataset.containsNamedModel(URImessagePayloadGraph)) {
      logger.debug("Dataset with named payload")
      messageDataset.getNamedModel(URImessagePayloadGraph)
    } else {
      logger.debug("Dataset without named payload")
      withPayload = false
      messageDataset.getDefaultModel
//      throw EmptyPayloadException()
    }
  }

  def setPayload(model: Model): Unit = {
    if (withPayload) {
      logger.debug("Adding namedModel to the messageDataset")
      messageDataset.addNamedModel(URImessagePayloadGraph, model)
    } else {
      logger.debug("Setting defaultModel for the messageDataset")
      messageDataset.setDefaultModel(messageDataset.getDefaultModel.union(model))  //replaceNamedModel(URImessagePayloadGraph,model)
    }
  }

  def serialize: String = writeDatasetToJSONLD()

  def serializePayload: String = payloadAsJsonLd()

  @unused
  def serializePayloadAsRdfXml: String = payloadAsRdfXml()

  @inline
  final private def updateContext(jsonLdString: String): String = {
    val ctxtPat = """("@context"[^{]+\{[^}]+)}""".r
    val jsonLdStr = ctxtPat.replaceAllIn(jsonLdString, "$1,\"InterIoTMsg\":\"http://inter-iot.eu/message/\"}")
    jsonLdStr
  }

  private def readDatasetFromJSONLD(jsonLdString: String): Dataset = {
    val jenaDataset = DatasetFactory.create()
    val jsonLdStr = updateContext(jsonLdString)
    val inStream = new ByteArrayInputStream(jsonLdStr.getBytes)
    RDFDataMgr.read(jenaDataset, inStream, Lang.JSONLD)
    if (jenaDataset.containsNamedModel(URImessagePayloadGraph)) {
      logger.debug("Dataset with named payload created")
    } else {
      logger.debug("Dataset without named payload created")
      withPayload = false
    }
    jenaDataset
  }

  private def payloadAsRdfXml(): String = {
    val outStream = new ByteArrayOutputStream
    val model = if (withPayload) {
      messageDataset.getNamedModel(URImessagePayloadGraph)
    } else {
      messageDataset.getDefaultModel
    }
    RDFDataMgr.write(outStream, model, Lang.RDFXML)
    outStream.toString
  }

  private def payloadAsJsonLd(): String = {
    val outStream = new ByteArrayOutputStream
    val model = if (withPayload) {
      logger.debug("payloadAsJsonLd with named payload")
      messageDataset.getNamedModel(URImessagePayloadGraph)
    } else {
      logger.debug("payloadAsJsonLd without named payload")
      messageDataset.getDefaultModel
    }
    RDFDataMgr.write(outStream, model, Lang.JSONLD)
    outStream.toString
  }

  private def writeDatasetToJSONLD(): String = {
    val outStream = new ByteArrayOutputStream
    RDFWriter.create()
      .format(RDFFormat.JSONLD10_COMPACT_PRETTY)
      .source(messageDataset.asDatasetGraph())
      .context(Message.jsonLDWriteContext)
      .output(outStream)

    outStream.toString
  }

}

object Message {
  def apply(jsonLdStr: String): Message = new Message(jsonLdStr)

  private val jsonLDWriteContext = {
    val tContext = new JsonLDWriteContext()
    val opts = new JsonLdOptions()
    //      opts.setEmbed(false)
    //      opts.setExplicit(true)
    //      opts.setOmitDefault(false)
    opts.setUseRdfType(true)
    tContext.setOptions(opts)
    tContext
  }

}

/*
  > The documentation states that the returned object is for one-time use only.
  > The feature request is to make it possible to write out streams of Datasets
  > in a  push-based manner. Thereby the writer should maintain state information
  > such that prefixes and base IRIs are not written out redundantly.
  > {code:java}
  > try(OutputStream out = ...} {
  >   StreamWriterDatasetRIOT sink = RDFDataMgr.createStreamDatasetWriter(out,
  > RDFFormat.TURTLE_PRETTY);
  >   sink.start(); // May immediately trigger a write on the output stream
  >   for (Dataset ds : streamOfDatasets) {
  >     sink.send(ds);
  >     sink.flush();
  >   }
  >   sink.finish(); // Write out footer and free resources
  >   // Is tthere is a need for sink.close()?
  > } // close resources
  > {code}
*/
