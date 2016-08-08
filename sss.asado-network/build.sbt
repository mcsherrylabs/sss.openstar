
scalacOptions += "-target:jvm-1.8"

parallelExecution in Test := false

name := "sss.asado-network"

version := "0.2.9-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test

libraryDependencies += "com.typesafe.akka" %% "akka-testkit"  % "2.4.+" % Test

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "mcsherrylabs.com" %% "scrypto" % "1.2.0-SNAPSHOT"

libraryDependencies += "commons-net" % "commons-net" % "3.+"

libraryDependencies += "com.google.guava" % "guava" % "16.+"

libraryDependencies += "mcsherrylabs.com" %% "sss-ancillary" % "0.9.13"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.+"

libraryDependencies += "com.typesafe.akka" %% "akka-agent" % "2.4.+"

libraryDependencies += "org.bitlet" % "weupnp" % "0.1.+"


