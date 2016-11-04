
name := "sss.asado-common"

scalaVersion := "2.11.8"

version := "0.3-SNAPSHOT"

//resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

libraryDependencies += "org.consensusresearch" %% "scrypto" % "1.2.0-RC3"

libraryDependencies += "com.mcsherrylabs" %% "sss-ancillary" % "1.1-SNAPSHOT"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.+"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.5" % Test



