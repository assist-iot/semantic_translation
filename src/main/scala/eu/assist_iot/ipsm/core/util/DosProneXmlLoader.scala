package eu.assist_iot.ipsm.core.util

// "Solution" taken from: https://github-wiki-see.page/m/scala/scala-xml/wiki/Safer-parser-defaults
// WARNING! This is NOT a real "solution" as it opens IPSM for possible serious DoS-type attacks.
import javax.xml.XMLConstants
import scala.xml.Elem
import scala.xml.factory.XMLLoader
import javax.xml.parsers.{SAXParser, SAXParserFactory}

object DosProneXmlLoader extends XMLLoader[Elem] {
  override def parser: SAXParser = {
    val factory = SAXParserFactory.newInstance()
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
    factory.newSAXParser()
  }
}
