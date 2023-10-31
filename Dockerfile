# GraalVM CE 22.3.3-b1 Java 17, Scala 2.13.12, SBT 1.9.7
FROM sbtscala/scala-sbt:graalvm-ce-22.3.3-b1-java17_1.9.7_2.13.12 as builder

# Copy the project sources
COPY . /app

# Build the project
WORKDIR /app
RUN sbt assembly

# Create the final image
FROM eclipse-temurin:21-jre-alpine
MAINTAINER "Wiesław Pawłowski <wieslaw.pawlowski@ibspan.waw.pl>"

# Copy the jar
COPY --from=builder /app/target/scala-2.13/semantic-translation-assembly.jar /app/

RUN mkdir -p /data
COPY --from=builder /app/data/ipsm.sqlite /data/
WORKDIR /app
ENTRYPOINT ["java", "-Dconfig.resource=nossl.conf", "-jar", "semantic-translation-assembly.jar"]

