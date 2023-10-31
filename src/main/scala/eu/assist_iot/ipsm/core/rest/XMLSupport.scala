package eu.assist_iot.ipsm.core.rest

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import eu.assist_iot.ipsm.core.config.Prefixes
import eu.assist_iot.ipsm.core.datamodel.{AlignmentConfig, AlignmentData}

import scala.xml.XML

trait XMLSupport {

  implicit val um : Unmarshaller[HttpEntity, AlignmentConfig] = {
    Unmarshaller.byteStringUnmarshaller.forContentTypes(MediaTypes.`application/xml`).mapWithCharset { (data, charset) =>
      val input =
        if (charset == HttpCharsets.`UTF-8`) {
          data.utf8String
        } else {
          data.decodeString(charset.nioCharset.name)
        }
      val xml = XML.loadString(input)

      AlignmentConfig((xml \ "Alignment" \ "title").filter(_.namespace == Prefixes("dc")).text,
        (xml \ "Alignment" \ "onto1" \ "Ontology" \ ("@{" + Prefixes("rdf") + "}about")).text,
        (xml \ "Alignment" \ "onto2" \ "Ontology" \ ("@{" + Prefixes("rdf") + "}about")).text,
        (xml \ "Alignment" \ "version").filter(_.namespace == Prefixes("exmo")).text,
        (xml \ "Alignment" \ "creator").filter(_.namespace == Prefixes("dc")).text,
        (xml \ "Alignment" \ "description").filter(_.namespace == Prefixes("dc")).text, input
      )
    }
  }

  private val `xml` =  MediaType.applicationWithFixedCharset("xml", HttpCharsets.`UTF-8`)

  implicit def alignmentMarshaller: ToEntityMarshaller[AlignmentData] = Marshaller.oneOf(
    Marshaller.withFixedContentType(`xml`) { align =>
      HttpEntity(`xml`, align.xmlSource)
    })

}
