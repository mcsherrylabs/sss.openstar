logLevel := Level.Warn

classpathTypes += "maven-plugin"

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.5")

resolvers += "stepsoft-snapshots" at "http://nexus.mcsherrylabs.com/repository/snapshots"

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.8-SNAPSHOT" changing())

// For code formatting
//addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.15")
