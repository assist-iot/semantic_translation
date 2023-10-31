package eu.assist_iot.ipsm.core.config

object Prefixes {

  import collection.mutable.{Map => MMap}

  val prefs: MMap[String, String] = MMap(
    "sripas" -> "http://www.inter-iot.eu/sripas#",
    "rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs" -> "http://www.w3.org/2000/01/rdf-schema#",
    "ont1" -> "http://www.example.org/ontology1#",
    "ont2" -> "http://www.example.org/ontology2#",
    "ont3" -> "http://www.example.org/ontology3#",
    "ont4" -> "http://www.example.org/ontology4#",
    "ont5" -> "http://www.example.org/ontology5#",
    "ont6" -> "http://www.example.org/ontology6#",
    "owl" -> "http://www.w3.org/2002/07/owl#",
    "xsd" -> "http://www.w3.org/2001/XMLSchema#",
    "iot-lite" -> "http://purl.oclc.org/NET/UNIS/fiware/iot-lite#",
    "ns" -> "http://creativecommons.org/ns#",
    "georss" -> "http://www.georss.org/georss/",
    "OrientDB" -> "http://www.dewi.org/WDA.owl#OrientDB",
    "ACS" -> "http://www.dewi.org/ACS.owl#",
    "WDA" -> "http://www.dewi.org/WDA.owl#",
    "terms" -> "http://purl.org/dc/terms/",
    "xml" -> "http://www.w3.org/XML/1998/namespace#",
    "wgs84_pos" -> "http://www.w3.org/2003/01/geo/wgs84_pos#",
    "DUL" -> "http://www.loa-cnr.it/ontologies/DUL.owl#",
    "foaf" -> "http://xmlns.com/foaf/0.1/",
    "dc" -> "http://purl.org/dc/elements/1.1/",
    "my_port" -> "http://example.sripas.org/ontologies/port.owl#",
    "exmo" -> "http://exmo.inrialpes.fr/align/ext/1.0/#",
    "align" -> "http://knowledgeweb.semanticweb.org/heterogeneity/alignment#",
    "iiot" -> "http://inter-iot.eu/GOIoTP#",
    "iiotex" -> "http://inter-iot.eu/GOIoTPex#",
    "InterIoTMsg" -> "http://inter-iot.eu/message/"
  )

  def apply(key: String): String = prefs(key)

  def get(key: String): Option[String] = prefs.get(key)

  def set(key: String, prefix: String): Unit = {
    prefs += (key -> prefix)
  }

}
