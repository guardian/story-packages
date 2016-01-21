name := "story-packages"

version := "1.0.0"

maintainer := "CMS Fronts <aws-cms-fronts@theguardian.com>"

packageSummary := "Story packages"

packageDescription := "Guardian story packages editor"

scalaVersion := "2.11.7"

import com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd
serverLoading in Debian := Systemd

import com.twitter.scrooge._

debianPackageDependencies := Seq("openjdk-8-jre-headless")

def env(key: String): Option[String] = Option(System.getenv(key))

riffRaffPackageType := (packageBin in Debian).value
riffRaffBuildIdentifier := env("TRAVIS_BUILD_NUMBER").getOrElse("DEV")
riffRaffManifestBranch := env("TRAVIS_BRANCH").getOrElse(git.gitCurrentBranch.value)
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")


javacOptions := Seq("-g","-encoding", "utf8")

javaOptions in Universal ++= Seq(
    "-Dpidfile.path=/dev/null",
    "-J-XX:MaxRAMFraction=2",
    "-J-XX:InitialRAMFraction=2",
    "-J-XX:MaxMetaspaceSize=500m",
    "-J-XX:+PrintGCDetails",
    "-J-XX:+PrintGCDateStamps",
    s"-J-Xloggc:/var/log/${packageName.value}/gc.log"
)

scalacOptions := Seq("-unchecked", "-optimise", "-deprecation", "-target:jvm-1.8",
      "-Xcheckinit", "-encoding", "utf8", "-feature", "-Yinline-warnings","-Xfatal-warnings")

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

TwirlKeys.templateImports ++= Seq(
    "conf._",
    "play.api.Play",
    "play.api.Play.current"
)


val awsVersion = "1.10.47"

libraryDependencies ++= Seq(
    ws,
    filters,
    "com.amazonaws" % "aws-java-sdk-core" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-kinesis" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-s3" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-sqs" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-sts" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-dynamodb" % awsVersion,
    "com.gu" %% "content-api-client" % "7.19",
    "com.gu" %% "fapi-client" % "0.68",
    "com.gu" % "kinesis-logback-appender" % "1.2.0",
    "com.gu" %% "pan-domain-auth-play_2-4-0" % "0.2.11",
    "com.gu" %% "story-packages-model" % "0.4.0",
    "net.logstash.logback" % "logstash-logback-encoder" % "4.5.1",
    "org.julienrf" %% "play-json-variants" % "2.0",
    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "org.scalatestplus" %% "play" % "1.4.0" % "test",
    "org.apache.thrift" % "libthrift" % "0.9.3",
    "com.twitter" %% "scrooge-core" % "3.20.0"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala, RiffRaffArtifact, JDebPackaging)
