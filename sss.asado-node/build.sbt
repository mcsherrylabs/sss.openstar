name := "sss.asado-node"

packageSummary in Linux := "asado node"

enablePlugins(JavaAppPackaging) //, DockerPlugin)

//dockerEntrypoint := Seq("/opt/docker/bin/core_node")

//dockerExposedPorts := Seq(7070, 7071, 7072, 7073, 7074, 7075, 7076, 7077, 7078, 7079)

//dockerExposedPorts := Seq(7070, 8071, 7071, 8070)

parallelExecution in Test := false

libraryDependencies += "com.typesafe.akka" %% "akka-testkit"  % Vers.akkaVer % Test

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % Vers.akkaVer

libraryDependencies += "com.typesafe.akka" %% "akka-agent" % Vers.akkaVer

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-slf4j_2.11
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % Vers.akkaVer

libraryDependencies += "org.scalactic" %% "scalactic" % Vers.scalacticVer

libraryDependencies += "commons-net" % "commons-net" % Vers.commonsNetVer

//libraryDependencies += "com.google.guava" % "guava" % "16.+"

libraryDependencies += "com.mcsherrylabs" %% "sss-db" % Vers.sssDbVer

libraryDependencies += "com.mcsherrylabs" %% "sss-console-util" % Vers.consoleUtilVer

//libraryDependencies += "org.bitlet" % "weupnp" % "0.1.+"

//libraryDependencies += "com.twitter" %% "util-collection" % Vers.twitterColVer

libraryDependencies += "org.scalatra" %% "scalatra" % Vers.scalatraVer

libraryDependencies += "io.spray" %%  "spray-json" % Vers.sprayJsonVer

libraryDependencies += "us.monoid.web" % "resty" % Vers.restyVer % Test

// https://mvnrepository.com/artifact/org.hsqldb/hsqldb
libraryDependencies += "org.hsqldb" % "hsqldb" % Vers.hsqldbVer

//updateOptions := updateOptions.value.withCachedResolution(false)

mainClass in Compile := Some("sss.asado.Main")

