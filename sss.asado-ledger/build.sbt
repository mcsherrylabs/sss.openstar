name := "sss.asado-ledger"

scalaVersion := "2.11.8"

version := "0.2.9-SNAPSHOT"

parallelExecution in Test := false

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

libraryDependencies += "mcsherrylabs.com" %% "sss-asado-common" % "0.2.9-SNAPSHOT"

libraryDependencies += "mcsherrylabs.com" %% "sss-ancillary" % "0.9.+"

libraryDependencies += "mcsherrylabs.com" %% "sss-db" % "0.9.29"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test
