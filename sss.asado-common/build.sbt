
name := "sss.asado-common"

scalaVersion := "2.11.8"

version := "0.2.9-SNAPSHOT"

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

libraryDependencies += "mcsherrylabs.com" %% "scrypto" % "1.2.0-SNAPSHOT"

libraryDependencies += "mcsherrylabs.com" %% "sss-ancillary" % "0.9.13"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.+"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.5" % Test



