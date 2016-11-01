enablePlugins(JavaAppPackaging, DockerPlugin)

dockerEntrypoint := Seq("/opt/docker/bin/core_node")

//dockerExposedPorts := Seq(7070, 7071, 7072, 7073, 7074, 7075, 7076, 7077, 7078, 7079)

dockerExposedPorts := Seq(7070, 8071, 7071, 8070)

scalacOptions += "-target:jvm-1.8"

parallelExecution in Test := false

name := "sss.asado-node"

version := "0.2.11-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

packageSummary in Linux := "asado node"

dependencyOverrides += "org.consensusresearch" %% "scrypto" % "1.2.0-RC3"

dependencyOverrides += "com.mcsherrylabs" %% "sss-ancillary" % "1.1-SNAPSHOT"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test

libraryDependencies += "com.typesafe.akka" %% "akka-testkit"  % "2.4.+" % Test

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "commons-net" % "commons-net" % "3.+"

libraryDependencies += "com.google.guava" % "guava" % "16.+"

libraryDependencies += "com.mcsherrylabs" %% "sss-db" % "0.9.33"

libraryDependencies += "com.mcsherrylabs" %% "sss-console-util" % "0.1.2"

libraryDependencies += "com.mcsherrylabs" %% "sss-asado-network" % "0.2.9-SNAPSHOT"

libraryDependencies += "com.mcsherrylabs" %% "sss-asado-common" % "0.2.9-SNAPSHOT"

libraryDependencies += "com.mcsherrylabs" %% "sss-asado-ledger" % "0.2.9-SNAPSHOT"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.+"

libraryDependencies += "com.typesafe.akka" %% "akka-agent" % "2.4.+"

libraryDependencies += "org.bitlet" % "weupnp" % "0.1.+"

libraryDependencies += "com.twitter" %% "util-collection" % "6.27.0"

libraryDependencies += "org.scalatra" % "scalatra_2.11" % "2.4.0"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.2"

libraryDependencies += "us.monoid.web" % "resty" % "0.3.2" % Test

// https://mvnrepository.com/artifact/org.hsqldb/hsqldb
libraryDependencies += "org.hsqldb" % "hsqldb" % "2.3.4"

//updateOptions := updateOptions.value.withCachedResolution(false)

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-slf4j_2.11
libraryDependencies += "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.8"

mainClass in Compile := Some("sss.asado.CoreMain")

