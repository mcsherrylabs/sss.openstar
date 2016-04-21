enablePlugins(JavaAppPackaging)

scalacOptions += "-target:jvm-1.8"

parallelExecution in Test := false

name := "sss.asado"

version := "0.1"

scalaVersion := "2.11.8"

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

packageSummary in Linux := "asado node"

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

libraryDependencies += "org.consensusresearch" %% "scrypto" % "1.+"

libraryDependencies += "commons-net" % "commons-net" % "3.+"

libraryDependencies += "com.google.guava" % "guava" % "16.+"

libraryDependencies += "mcsherrylabs.com" %% "sss-ancillary" % "0.9.6"

libraryDependencies += "mcsherrylabs.com" %% "sss-db" % "0.9.21"

libraryDependencies += "mcsherrylabs.com" %% "sss-console-util" % "0.1.1"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.+"

libraryDependencies += "com.typesafe.akka" %% "akka-agent" % "2.4.+"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit"  % "2.4.+" % "test"

libraryDependencies += "org.bitlet" % "weupnp" % "0.1.+"

libraryDependencies += "com.twitter" %% "util-collection" % "6.27.0"

libraryDependencies += "org.scalatra" % "scalatra_2.11" % "2.4.0"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.2"

libraryDependencies += "org.eclipse.jetty.aggregate" % "jetty-all-server" % "8.1.18.v20150929"

mainClass in Compile := Some("sss.asado.Node")
