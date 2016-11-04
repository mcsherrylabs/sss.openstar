name := "sss.asado-ledger"

scalaVersion := "2.11.8"

version := "0.3-SNAPSHOT"

parallelExecution in Test := false

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

libraryDependencies += "com.mcsherrylabs" %% "sss-asado-common" % "0.3-SNAPSHOT"

libraryDependencies += "com.mcsherrylabs" %% "sss-ancillary" % "1.1-SNAPSHOT"

libraryDependencies += "com.mcsherrylabs" %% "sss-db" % "0.9.33"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test
