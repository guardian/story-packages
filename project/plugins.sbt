// Additional information on initialization
logLevel := Level.Warn

resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Resolver.sonatypeRepo("releases"),
  Resolver.typesafeRepo("releases"),
  "Spy" at "https://files.couchbase.com/maven2/"
)

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.19")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.3")

addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "1.1.12")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

libraryDependencies += "org.vafer" % "jdeb" % "1.3" artifacts (Artifact("jdeb", "jar", "jar"))
