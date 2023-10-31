package eu.assist_iot.ipsm.core.message.test

import eu.assist_iot.ipsm.core.datamodel.Message
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source

class MessagePayloadTests extends AnyFlatSpec with Matchers {

  val fnames = List(
    "inp/demoMsg1",
    "inp/demoMsg1CO",
    "inp/demoMsg2",
    "inp/demoMsg3",
    "inp/demoMsg3CO",
    "inp/demoMsg34",
    "inp/demoMsg34CO",
    "inp/demoMsg5",
    "inp/demoMsg6"
  )

  fnames.foreach { name =>
    s"""Message "data/$name.json"""" should "parse into (non-null) Jena Dataset" in {
      val jsonFile = s"data/$name.json"
      val jsoSource = Source.fromResource(jsonFile).getLines().mkString("\n")
      val dataSet = new Message(jsoSource)
      dataSet shouldNot be (null)  // scalastyle:ignore
    }

    it should """contain payload""" in {
      val jsonFile = s"data/$name.json"
      val jsonSource = Source.fromResource(jsonFile).getLines().mkString("\n")
      val dataSet = new Message(jsonSource)
      dataSet.getPayload shouldNot be (null)  // scalastyle:ignore
    }
  }

}
