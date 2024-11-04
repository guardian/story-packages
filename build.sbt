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
    s"-J-Xloggc:/var/log/${packageName.value}/gc.log"
)

scalacOptions := Seq("-unchecked", "-deprecation", "-release:8",
      "-Xcheckinit", "-encoding", "utf8", "-feature", "-Xfatal-warnings")

Compile / doc / sources := Seq.empty

Compile / packageDoc / publishArtifact := false

val awsVersion = "1.12.770"
val capiModelsVersion = "25.1.0"
val json4sVersion = "4.0.7"

resolvers ++= Seq(
    Resolver.file("Local", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
)

buildInfoPackage := "app"
buildInfoKeys += "gitCommitId" -> env.getOrElse("GITHUB_SHA", "Unknown")

lazy val jacksonVersion = "2.17.2"

// these Jackson dependencies are required to resolve issues in Play 2.8.x https://github.com/orgs/playframework/discussions/11222
val jacksonOverrides = Seq(
    "com.fasterxml.jackson.core" % "jackson-core"  % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-annotations"  % jacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
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
    "com.gu" %% "content-api-client-aws" % "0.7.5",
    "com.gu" %% "fapi-client-play30" % "12.0.0",
    "com.gu" %% "pan-domain-auth-play_3-0" % "4.0.0",
    "com.gu" %% "editorial-permissions-client" % "2.15",
    "com.gu" %% "story-packages-model" % "2.2.0",
    "com.gu" %% "thrift-serializer" % "4.0.2",
    "org.json4s" %% "json4s-native" % json4sVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "net.logstash.logback" % "logstash-logback-encoder" % "7.2",
    "org.playframework" %% "play-json" % "3.0.4",
    "org.julienrf" %% "play-json-derived-codecs" % "11.0.0",
    "org.scalatest" %% "scalatest" % "3.2.15" % "test"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala, JDebPackaging, SystemdPlugin, BuildInfoPlugin)
