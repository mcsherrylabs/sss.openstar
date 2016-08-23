import NativePackagerHelper._

enablePlugins(JavaAppPackaging)

packageSummary in Linux := "message-load-test"
scalacOptions += "-target:jvm-1.8"

parallelExecution in Test := false

name := "message-sender"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

dependencyOverrides += "mcsherrylabs.com" %% "scrypto" % "1.2.0-SNAPSHOT"

dependencyOverrides += "mcsherrylabs.com" %% "sss-ancillary" % "0.9.13"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test

libraryDependencies += "com.typesafe.akka" %% "akka-testkit"  % "2.4.+" % Test

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "commons-net" % "commons-net" % "3.+"

libraryDependencies += "com.google.guava" % "guava" % "16.+"

libraryDependencies += "mcsherrylabs.com" %% "sss-db" % "0.9.33"

libraryDependencies += "mcsherrylabs.com" %% "sss-console-util" % "0.1.2"

libraryDependencies += "mcsherrylabs.com" %% "sss-asado-network" % "0.2.9-SNAPSHOT"

libraryDependencies += "mcsherrylabs.com" %% "sss-asado-node" % "0.2.11-SNAPSHOT"

libraryDependencies += "mcsherrylabs.com" %% "sss-asado-common" % "0.2.9-SNAPSHOT"

libraryDependencies += "mcsherrylabs.com" %% "sss-asado-ledger" % "0.2.9-SNAPSHOT"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.+"

libraryDependencies += "com.typesafe.akka" %% "akka-agent" % "2.4.+"

libraryDependencies += "org.scalatra" % "scalatra_2.11" % "2.4.0"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.2"

libraryDependencies += "us.monoid.web" % "resty" % "0.3.2" % Test

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-slf4j_2.11
libraryDependencies += "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.8"

mainClass in Compile := Some("messagesender.Main")

