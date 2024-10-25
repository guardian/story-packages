import scala.sys.env

name := "story-packages"

version := "1.0.0"

maintainer := "CMS Fronts <aws-cms-fronts@theguardian.com>"

packageSummary := "Story packages"

packageDescription := "Guardian story packages editor"

scalaVersion := "2.13.14"

import sbt.Resolver

debianPackageDependencies := Seq("java11-runtime-headless")

javacOptions := Seq("-g","-encoding", "utf8")

Universal / javaOptions ++= Seq(
    "-Dpidfile.path=/dev/null",
    "-J-XX:MaxRAMFraction=2",
    "-J-XX:InitialRAMFraction=2",
    "-J-XX:MaxMetaspaceSize=500m",
    "-J-XX:+PrintGCDetails",
    "-J-XX:+PrintGCDateStamps",
    s"-J-Xloggc:/var/log/${packageName.value}/gc.log"
)

scalacOptions := Seq("-unchecked", "-deprecation", "-release:8",
      "-Xcheckinit", "-encoding", "utf8", "-feature", "-Xfatal-warnings")

Compile / doc / sources := Seq.empty

Compile / packageDoc / publishArtifact := false

val awsVersion = "1.11.999"
val capiModelsVersion = "17.4.0"
val json4sVersion = "4.0.3"

resolvers ++= Seq(
    Resolver.file("Local", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
)

buildInfoPackage := "app"
buildInfoKeys += "gitCommitId" -> env.getOrElse("GITHUB_SHA", "Unknown")

lazy val jacksonVersion = "2.13.4"
lazy val jacksonDatabindVersion = "2.13.4.2"

// these Jackson dependencies are required to resolve issues in Play 2.8.x https://github.com/orgs/playframework/discussions/11222
val jacksonOverrides = Seq(
    "com.fasterxml.jackson.core" % "jackson-core"  % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-annotations"  % jacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion
)

libraryDependencies ++= jacksonOverrides ++  Seq(
    ws,
    filters,
    "com.amazonaws" % "aws-java-sdk-core" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-kinesis" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-s3" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-sqs" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-sts" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-dynamodb" % awsVersion,
    "com.gu" %% "content-api-models-scala" % capiModelsVersion,
    "com.gu" %% "content-api-models-json" % capiModelsVersion,
    "com.gu" %% "content-api-client-aws" % "0.7",
    "com.gu" %% "fapi-client-play28" % "4.0.4",
    "com.gu" %% "pan-domain-auth-play_2-8" % "4.0.0",
    "com.gu" %% "story-packages-model" % "2.2.0",
    "com.gu" %% "thrift-serializer" % "4.0.2",
    "org.json4s" %% "json4s-native" % json4sVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "net.logstash.logback" % "logstash-logback-encoder" % "7.2",
    "com.typesafe.play" %% "play-json" % "2.9.4",
    "org.julienrf" %% "play-json-derived-codecs" % "10.1.0",
    "org.scalatest" %% "scalatest" % "3.2.15" % "test"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala, JDebPackaging, SystemdPlugin, BuildInfoPlugin)
