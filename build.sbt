Global / onChangedBuildSource := ReloadOnSourceChanges
Global / excludeLintKeys += buildInfoKeys
Global / excludeLintKeys += buildInfoObject
Global / excludeLintKeys += buildInfoPackage

lazy val Version = new {
  val Akka                      = "2.6.21"
  val AkkaHttp                  = "10.2.10"
  val AkkaHttpCors              = "1.2.0"
  val AkkaHttpMetricsPrometheus = "1.7.1"
  val AkkaStreamKafka           = "3.0.1"
  val CommonsIo                 = "2.14.0"
  val Dom4J                     = "2.1.4"
  val JacksonDataformatYaml     = "2.13.5"
  val Jaxen                     = "2.0.0"
  val Jena                      = "4.7.0"
  val JsonLdJava                = "0.13.4"
  val KafkaClients              = "3.4.1"
  val Log4S                     = "1.10.0"
  val LogbackClassic            = "1.4.11"
  val MqttWrapper               = "4.0.0"
  val QuillJdbc                 = "4.6.1"
  val ScalaXml_for_2_13         = "1.3.1"
  val ScalaConfig               = "1.4.3"
  val ScalaTest                 = "3.2.17"
  val Scallop                   = "4.1.0"
  val Shacl                     = "1.4.2"
  val SprayJson                 = "1.3.6"
  val SqliteJdbc                = "3.43.2.1"
}

lazy val commonSettings = Seq(
  name := "semantic-translation",
  organization := "eu.assist-iot",
  version := "1.0.0",
  scalaVersion := "2.13.12",
  scalacOptions := Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding",
    "utf8"
  ),
  fork := true,
  reStart / javaOptions += "-Dfile.encoding=utf8",
  javaOptions += "-Dconfig.resource=ssl.conf",
  libraryDependencies ++= Seq(
    "org.rogach"                       %% "scallop"                      % Version.Scallop,
    "commons-io"                        % "commons-io"                   % Version.CommonsIo,
    "com.github.jsonld-java"            % "jsonld-java"                  % Version.JsonLdJava,
    "com.typesafe"                      % "config"                       % Version.ScalaConfig,
    "io.getquill"                      %% "quill-jdbc"                   % Version.QuillJdbc,
    "org.xerial"                        % "sqlite-jdbc"                  % Version.SqliteJdbc,
    "org.apache.kafka"                  % "kafka-clients"                % Version.KafkaClients,
    "org.apache.jena"                   % "jena-base"                    % Version.Jena,
    "org.apache.jena"                   % "jena-core"                    % Version.Jena,
    "org.apache.jena"                   % "jena-arq"                     % Version.Jena,
    "org.topbraid"                      % "shacl"                        % Version.Shacl,
    "org.dom4j"                         % "dom4j"                        % Version.Dom4J,
    "jaxen"                             % "jaxen"                        % Version.Jaxen,
    "org.scalatest"                    %% "scalatest"                    % Version.ScalaTest % "test",
    "org.scala-lang.modules"           %% "scala-xml"                    % Version.ScalaXml_for_2_13,
    "com.fasterxml.jackson.dataformat"  % "jackson-dataformat-yaml"      % Version.JacksonDataformatYaml,
    "ch.qos.logback"                    % "logback-classic"              % Version.LogbackClassic,
    "org.log4s"                        %% "log4s"                        % Version.Log4S,
    "io.spray"                         %% "spray-json"                   % Version.SprayJson,
    "com.typesafe.akka"                %% "akka-slf4j"                   % Version.Akka,
    "com.typesafe.akka"                %% "akka-actor"                   % Version.Akka,
    "com.typesafe.akka"                %% "akka-stream"                  % Version.Akka,
    "io.github.assist-iot-sripas"      %% "scala-mqtt-wrapper-akka"      % Version.MqttWrapper,
    "com.typesafe.akka"                %% "akka-stream-kafka"            % Version.AkkaStreamKafka,
    "com.typesafe.akka"                %% "akka-http"                    % Version.AkkaHttp,
    "com.typesafe.akka"                %% "akka-http-core"               % Version.AkkaHttp,
    "com.typesafe.akka"                %% "akka-http-spray-json"         % Version.AkkaHttp,
    "ch.megard"                        %% "akka-http-cors"               % Version.AkkaHttpCors,
    "fr.davit"                         %% "akka-http-metrics-prometheus" % Version.AkkaHttpMetricsPrometheus
  ),
//  assembly / assemblyDefaultJarName := s"${name.value}-assembly-${version.value}.jar"
  assembly / assemblyJarName := "sem-trans-assembly.jar",
)

lazy val assemblySettings = sbtassembly.AssemblyPlugin.assemblySettings ++ Seq(
  assembly / assemblyMergeStrategy := {
    case PathList("version.conf") => MergeStrategy.concat
    case PathList("reference.conf") => MergeStrategy.concat
    case PathList("logback-test.xml") => MergeStrategy.discard
    case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
    case PathList("org", "scala-lang", "modules", "scala-xml", _*) => MergeStrategy.first
    case PathList("org", "apache", "commons", "codec", _*) => MergeStrategy.first
    case PathList("javax", "xml", _*) => MergeStrategy.first
    // case PathList("application.conf") => MergeStrategy.discard
    // The above would be an error, since rename below is executed before any other MergeStrategy
    case PathList("application-docker.conf") =>
      CustomMergeStrategy.rename(_ => "application.conf")
    case x if x.endsWith("module-info.class") => MergeStrategy.discard
    case x if x.endsWith("struct.proto") => MergeStrategy.last
    case x if x.endsWith("descriptor.proto") => MergeStrategy.last
    case x if x.endsWith("any.proto") => MergeStrategy.last
    case x if x.endsWith("empty.proto") => MergeStrategy.last
    case x if x.endsWith("version.conf") => MergeStrategy.last
    case x => MergeStrategy.defaultMergeStrategy(x)
  },
  assembly / mainClass := Some("eu.assist_iot.ipsm.core.Main"),
  assembly / test := {}
)

lazy val dockerSettings = Seq(
  docker / dockerfile := {
    val artifact: File = assembly.value
    val artifactTargetPath = s"/ipsm/${artifact.name}"
    new Dockerfile {
      from("eclipse-temurin:21-jre-alpine")
      maintainer("Wiesiek Pawłowski <wieslaw.pawlowski@ibspan.waw.pl>")
      add(artifact, artifactTargetPath)
      run("mkdir", "-p", "/data")
      copy(baseDirectory(_ / "data" / "ipsm.sqlite" ).value,"/data/ipsm.sqlite")
      volume("/data")
      env("IPSM_BROKER_TYPES.0", "MM")
      env("IPSM_MQTT_SRC_HOST", "host.docker.internal")
      env("IPSM_MQTT_SRC_PORT", "1883")
      env("IPSM_MQTT_TRG_HOST", "host.docker.internal")
      env("IPSM_MQTT_TRG_PORT", "1883")
      expose(8080)
      entryPoint("java", "-Dconfig.resource=nossl.conf", "-jar", artifactTargetPath)
    }
  },
  docker / imageNames := Seq(
    ImageName(s"docker.io/assistiot/semantic_translation:${version.value}")
  ),
  docker / buildOptions := BuildOptions(cache = false)
)

lazy val buildInfoSettings = Seq(
  compile / buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  compile / buildInfoPackage := "eu.assist_iot.ipsm.core",
  compile / buildInfoObject := "BuildInfo"
)

lazy val aggregatedSettings = commonSettings ++ assemblySettings ++ dockerSettings ++ buildInfoSettings

//noinspection scala2InSource3
lazy val ipsm_core = project
  .in(file("."))
  .enablePlugins(DockerPlugin, BuildInfoPlugin)
  .settings(aggregatedSettings: _*)

import sbtlicensereport.license.{LicenseInfo, DepModuleInfo}

licenseOverrides := {
  case DepModuleInfo("org.apache.commons", "commons-lang3", _) =>
    LicenseInfo(LicenseCategory.Apache, "The Apache Software License, Version 2.0", "https://opensource.org/licenses/Apache-2.0")
  case DepModuleInfo("ch.qos.logback", _, _) =>
    LicenseInfo(LicenseCategory.EPL, "Eclipse Public License 1.0", "https://opensource.org/licenses/EPL-1.0")
  case DepModuleInfo("com.typesafe.slick", _, _) =>
    LicenseInfo(LicenseCategory.BSD, "BSD 2-clause", "https://opensource.org/licenses/BSD-2-Clause")

}

licenseSelection := Seq(LicenseCategory.Apache, LicenseCategory.BSD, LicenseCategory.MIT, LicenseCategory.EPL)

//----------------------------------
// sbt-updates plugin configuration
//----------------------------------
// stop suggesting prehistoric version of the commons-io library as the newest
dependencyUpdatesFilter -=
  moduleFilter(
    organization = "commons-io",
    name = "commons-io",
    revision = "20030203.000550"
  )

// do not suggest upgrades to the BSL (Business Source License) versions of Akka-HTTP
dependencyUpdatesFilter -=
  moduleFilter(
    organization = "com.typesafe.akka",
    name = "akka-http*",
    revision = "10.4.*" | "10.5.*"
  )

// do not suggest upgrades to the BSL (Business Source License) versions of Akka
dependencyUpdatesFilter -=
  moduleFilter(
    organization = "com.typesafe.akka",
    name = "akka-*",
    revision = "2.7.*" | "2.8.*"
  )
