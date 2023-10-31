package eu.assist_iot.ipsm.core.rest.routes

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, HttpEntity, MediaType, StatusCodes}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.rest.{JsonSupport, XMLSupport}
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.{pathLabeled, pathPrefixLabeled}
import akka.http.scaladsl.server.Directives._


trait ApiExportRoutes extends JsonSupport with XMLSupport with LazyLogging {

  private lazy val swaggerJson: String = {
    val yamlReader = new ObjectMapper(new YAMLFactory)
    val obj = yamlReader.readValue(swaggerYaml, classOf[Any])
    val jsonWriter: ObjectMapper = new ObjectMapper
    jsonWriter.writeValueAsString(obj)
  }

  private lazy val swaggerYaml: String = {
    val source = scala.io.Source.fromResource("swagger.yaml")
    try source.getLines().mkString("\n") finally source.close()
  }

  private val yamlContentType = ContentType(
    MediaType.customWithFixedCharset(
      "application",
      "yaml",
      HttpCharsets.`UTF-8`
    )
  )

  val apiExportRoutes: Route = {
    pathPrefixLabeled("openapi") {
      complete(
        StatusCodes.OK,
        HttpEntity(ContentTypes.`application/json`, swaggerJson),
      )
    } ~
    pathLabeled("swagger.yaml") {
      complete(
        StatusCodes.OK,
        HttpEntity(yamlContentType, swaggerYaml),
      )
    } ~
    pathLabeled("swagger.json") {
      complete(
        StatusCodes.OK,
        HttpEntity(ContentTypes.`application/json`, swaggerJson),
      )
    } ~
    pathPrefixLabeled("docs") {
      pathEnd {
        extractUri { uri =>
          redirect(uri.copy(path = uri.path + "/"), StatusCodes.TemporaryRedirect)
        }
      } ~
        pathSingleSlash {
          getFromResource("swagger-ui/index.html")
        } ~
        getFromResourceDirectory("swagger-ui")
    }
  }

}
