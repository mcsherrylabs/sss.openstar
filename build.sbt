enablePlugins(JavaAppPackaging)

scalacOptions += "-target:jvm-1.8"

name := "sss.asado"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

packageSummary in Linux := "asado node"

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

libraryDependencies += "org.consensusresearch" %% "scrypto" % "1.+"

libraryDependencies += "commons-net" % "commons-net" % "3.+"

libraryDependencies += "com.google.guava" % "guava" % "15.+"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.4.+"

libraryDependencies += "mcsherrylabs.com" %% "sss-ancillary" % "0.9.6"

libraryDependencies += "mcsherrylabs.com" %% "sss-db" % "0.9.9"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.+"

libraryDependencies += "com.typesafe.akka" %% "akka-agent" % "2.4.+"

libraryDependencies += "org.bitlet" % "weupnp" % "0.1.+"

libraryDependencies += "com.chuusai" % "shapeless_2.11" % "1.2.4"

mainClass in Compile := Some("sss.asado.Node")
