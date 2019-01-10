import sbt.Keys.libraryDependencies

lazy val commonSettings = Seq(
  organization := "com.mcsherrylabs",
  version := "0.4.0-SNAPSHOT",
  scalaVersion := "2.12.6",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-target:jvm-1.8"),
  updateOptions := updateOptions.value.withGigahorse(false),
  updateOptions := updateOptions.value.withLatestSnapshots(false),
  resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/repository/releases",
  resolvers += "stepsoft-snapshots" at "http://nexus.mcsherrylabs.com/repository/snapshots",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
)


lazy val root = (project in file("."))
  .aggregate(common, network, ledger, node)

lazy val common  = (project in file("sss.openstar-common"))
  .settings(commonSettings)

lazy val network = (project in file("sss.openstar-network"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test")

lazy val ledger = (project in file("sss.openstar-ledger"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test")

lazy val node = (project in file("sss.openstar-node"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test", network, ledger)

lazy val ui_util = (project in file("sss.openstar-ui-util"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test")

lazy val nobu = (project in file("sss.openstar-nobu"))
  .settings(commonSettings)
  .dependsOn(node, ledger, ui_util)

lazy val analysis = (project in file("sss.openstar-analysis"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test", node, ui_util)

lazy val sandbox = (project in file("sandbox"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test", network)

lazy val telemetry = (project in file("sss.openstar-telemetry"))
  .settings(commonSettings)
  .dependsOn(common % "compile->compile;test->test")
