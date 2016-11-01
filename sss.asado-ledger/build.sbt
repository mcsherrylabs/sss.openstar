name := "sss.asado-ledger"

scalaVersion := "2.11.8"

version := "0.2.9-SNAPSHOT"

parallelExecution in Test := false

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

libraryDependencies += "com.mcsherrylabs" %% "sss-asado-common" % "0.2.9-SNAPSHOT"

libraryDependencies += "com.mcsherrylabs" %% "sss-ancillary" % "1.0"

libraryDependencies += "com.mcsherrylabs" %% "sss-db" % "0.9.33"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test
