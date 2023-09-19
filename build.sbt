import com.gu.riffraff.artifact.BuildInfo

import scala.sys.env

name := "story-packages"

version := "1.0.0"

maintainer := "CMS Fronts <aws-cms-fronts@theguardian.com>"

packageSummary := "Story packages"

packageDescription := "Guardian story packages editor"

scalaVersion := "2.12.16"

import sbt.Resolver

debianPackageDependencies := Seq("openjdk-8-jre-headless")

riffRaffPackageName := s"cms-fronts::${name.value}"
riffRaffManifestProjectName := riffRaffPackageName.value
riffRaffPackageType := (Debian / packageBin).value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources := {
    Seq(
        (Debian / packageBin).value -> s"${name.value}/${name.value}_${version.value}_all.deb",
        baseDirectory.value / "riff-raff.yaml" -> "riff-raff.yaml"
    )
}

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

scalacOptions := Seq("-unchecked", "-deprecation", "-target:jvm-1.8",
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
buildInfoKeys += "gitCommitId" -> BuildInfo(baseDirectory.value).revision

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
    "com.gu" % "kinesis-logback-appender" % "1.3.0",
    "com.gu" %% "pan-domain-auth-play_2-8" % "1.3.0",
    "com.gu" %% "story-packages-model" % "2.2.0",
    "com.gu" %% "thrift-serializer" % "4.0.0",
    "org.json4s" %% "json4s-native" % json4sVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "net.logstash.logback" % "logstash-logback-encoder" % "7.2",
    "com.typesafe.play" %% "play-json" % "2.9.4",
    "com.typesafe.play" %% "play-iteratees" % "2.6.1",
    "org.julienrf" %% "play-json-derived-codecs" % "10.1.0",
    "org.scalatest" %% "scalatest" % "3.2.15" % "test"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala, RiffRaffArtifact, JDebPackaging, SystemdPlugin, BuildInfoPlugin)
