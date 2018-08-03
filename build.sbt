
lazy val commonSettings = Seq(
  organization := "com.mcsherrylabs",
  version := "0.4.0-SNAPSHOT",
  scalaVersion := "2.12.6",
  scalacOptions += "-target:jvm-1.8",
  updateOptions := updateOptions.value.withGigahorse(false),
  resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/repository/releases",
  resolvers += "stepsoft-snapshots" at "http://nexus.mcsherrylabs.com/repository/snapshots",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
)


lazy val root = (project in file("."))
  .aggregate(common, network, ledger, node, nobu, analysis)

lazy val common  = (project in file("sss.asado-common"))
  .settings(commonSettings)

lazy val network = (project in file("sss.asado-network"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test")

lazy val ledger = (project in file("sss.asado-ledger"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test")

lazy val node = (project in file("sss.asado-node"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test", network, ledger)

lazy val nobu = (project in file("sss.asado-nobu"))
  .settings(commonSettings)
  .dependsOn(node, ledger)

lazy val analysis = (project in file("sss.asado-analysis"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test", node)

