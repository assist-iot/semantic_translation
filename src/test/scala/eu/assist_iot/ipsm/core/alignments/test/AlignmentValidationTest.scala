package eu.assist_iot.ipsm.core.alignments.test

import eu.assist_iot.ipsm.core.alignments.AlignmentValidator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source

class AlignmentValidationTest extends AnyFlatSpec with Matchers {

  val fnames = List(
    "UniversAAL_CO",
    "CO_FIWARE",
    "NOATUM_CO",
    "BodyCloud_CO"
  )

  fnames.foreach { name =>
    s"data/alignments/$name.rdf" should s"""validate against structure restrictions""" in {
      val xmlFile = s"data/alignments/$name.rdf"
      val xmlSource = Source.fromResource(xmlFile).getLines().mkString("\n")
      AlignmentValidator.validateWithJena(xmlSource) shouldEqual None
    }

    it should  """have valid alignment steps: """ + s"data/alignments/$name.rdf" in {
      val xmlFile = s"data/alignments/$name.rdf"
      val xmlSource = Source.fromResource(xmlFile).getLines().mkString("\n")
      val av = new AlignmentValidator(xmlSource)
      av.validateSteps() shouldEqual None
    }

    it should "for each cell have all <transformation> param variables contained in <entity1>: " + s"data/alignments/$name.rdf" in {
      val xmlFile = s"data/alignments/$name.rdf"
      val xmlSource = Source.fromResource(xmlFile).getLines().mkString("\n")
      val av = new AlignmentValidator(xmlSource)
      av.validateParamVariables() shouldEqual List()
    }

    it should "for each cell have all <entity2> variables contained in <entity1> and <return>: " + s"data/alignments/$name.rdf" in {
      val xmlFile = s"data/alignments/$name.rdf"
      val xmlSource = Source.fromResource(xmlFile).getLines().mkString("\n")
      val av = new AlignmentValidator(xmlSource)
      av.validateEntity2Variables() shouldEqual List()
    }

  }

}
