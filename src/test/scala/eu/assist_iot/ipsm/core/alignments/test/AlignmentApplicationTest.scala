package eu.assist_iot.ipsm.core.alignments.test

import java.io.{File, FileOutputStream, PrintWriter}
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.datamodel.Message
import eu.assist_iot.ipsm.core.alignments.Alignment
import org.apache.jena.rdf.model.ModelFactory
import org.scalatest._
import flatspec._
import org.scalatest.matchers.should.Matchers

import scala.io.Source

class AlignmentApplicationTest extends AnyFlatSpec with Matchers with LazyLogging {

  val fnames = List(
    ("weight.UniversAAL_CO", "CO_FIWARE"),
    ("bloodPressure.UniversAAL_CO", "CO_FIWARE"),
    ("Noatum", "NOATUM_CO"),
    ("bc-activity", "BodyCloud_CO"),
    ("bc-bloodPressure", "BodyCloud_CO"),
    ("bc-questionnaire", "BodyCloud_CO"),
    ("bc-weight", "BodyCloud_CO")
  )

  fnames.foreach { pair =>
    val (name, alignment) = pair

    s"inp/$name.json" should s"""succesfully translate via $alignment""" in {

      val jsonSource = Source.fromResource(s"data/inp/$name.json").getLines().mkString("\n")

      val xmlFile = s"data/alignments/$alignment.rdf"
      val xmlSource = Source.fromResource(xmlFile).getLines().mkString("\n")

      val message = new Message(jsonSource)
      val inpGraph = message.getPayload

      val inpGraphCopy = if (logger.underlying.isDebugEnabled) {
        ModelFactory.createDefaultModel().add(inpGraph)
      } else {
        null // scalastyle:ignore
      }

      val inpPayloadStr = if (logger.underlying.isDebugEnabled) {
        message.serializePayload
      } else {
        ""
      }

      val align = new Alignment(xmlSource)
      val outGraph = align.execute(inpGraph)
      message.setPayload(outGraph)

      new File("target/output").mkdirs()
      val writer = new PrintWriter(new FileOutputStream(s"target/output/$name.$alignment.json", false))
      writer.write(message.serialize)
      writer.close()

      logger.debug(
        s"""Translating "${pair._1}" via "${pair._2}"
           |SOURCE PAYLOAD:
           |$inpPayloadStr
           |${"-" * 80}
           |""".stripMargin)

      outGraph should not be null
      // TODO: The test below should eventually check wheather the outGraph is isomorphic to a predefined result
      // but there are some problems with checking it with Apache Jena ...
//      outGraph.isIsomorphicWith(inpGraphCopy) should be (false)
    }
  }
}
