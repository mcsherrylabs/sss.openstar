

enablePlugins(JavaAppPackaging)

packageSummary in Linux := "message-load-test"
scalacOptions += "-target:jvm-1.8"

parallelExecution in Test := false

name := "message-sender"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

dependencyOverrides += "org.consensusresearch" %% "scrypto" % "1.2.0-RC3"

dependencyOverrides += "com.mcsherrylabs" %% "sss-ancillary" % "1.1-SNAPSHOT"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test

libraryDependencies += "com.typesafe.akka" %% "akka-testkit"  % "2.4.+" % Test

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "commons-net" % "commons-net" % "3.+"

libraryDependencies += "com.google.guava" % "guava" % "16.+"

libraryDependencies += "com.mcsherrylabs" %% "sss-db" % "0.9.33"

libraryDependencies += "com.mcsherrylabs" %% "sss-console-util" % "0.1.2"

libraryDependencies += "com.mcsherrylabs" %% "sss-openstar-network" % "0.3-SNAPSHOT"

libraryDependencies += "com.mcsherrylabs" %% "sss-openstar-node" % "0.3-SNAPSHOT"

libraryDependencies += "com.mcsherrylabs" %% "sss-openstar-common" % "0.3-SNAPSHOT"

libraryDependencies += "com.mcsherrylabs" %% "sss-openstar-ledger" % "0.3-SNAPSHOT"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.+"

libraryDependencies += "com.typesafe.akka" %% "akka-agent" % "2.4.+"

libraryDependencies += "org.scalatra" % "scalatra_2.11" % "2.4.0"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.2"

libraryDependencies += "us.monoid.web" % "resty" % "0.3.2" % Test

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-slf4j_2.11
libraryDependencies += "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.8"

mainClass in Compile := Some("messagesender.Main")

