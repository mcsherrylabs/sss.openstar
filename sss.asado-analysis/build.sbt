import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._

enablePlugins(JavaAppPackaging)

packageSummary in Linux := "asado-analysis"

scalaVersion := "2.11.8"

version := "0.1"

resolvers += "indvd00m-github-repo" at "https://github.com/indvd00m/maven-repo/raw/master/repository"

resolvers += "Sonatype Nexus Releases" at "https://oss.sonatype.org/content/repositories/releases"

resolvers += "stepsoft" at "http://nexus.mcsherrylabs.com/nexus/content/groups/public"

resolvers += "vaadin-addons" at "http://maven.vaadin.com/vaadin-addons"

//Seq(vaadinWebSettings: _*)

val vaadinVer = "7.5.8"

dependencyOverrides += "com.mcsherrylabs" %% "sss-ancillary" % "1.0"

libraryDependencies ++= Seq(
  "org.hsqldb" % "hsqldb" % "2.3.4",
  "com.typesafe.akka" %% "akka-actor" % "2.4.0",
  "com.typesafe.akka" %% "akka-remote" % "2.4.0",
  "com.vaadin" % "vaadin-server" % vaadinVer,
  "com.vaadin" % "vaadin-themes" % vaadinVer,
  "com.vaadin" % "vaadin-push" % vaadinVer,
  "com.vaadin" % "vaadin-client-compiler" % vaadinVer,
  "com.vaadin" % "vaadin-client-compiled" % vaadinVer,
  "us.monoid.web" % "resty" % "0.3.2",
  "org.vaadin.icons" % "vaadin-icons" % "1.0.1",
  "org.vaadin.addons" % "animator" % "1.7.4",
  "com.mcsherrylabs" %% "sss-asado-node" % "0.3-SNAPSHOT",
  "com.mcsherrylabs" %% "sss-vaadin-akka-reactive" % "0.3-SNAPSHOT",
  "org.scalatra" % "scalatra_2.11" % "2.4.0",
  "io.spray" %%  "spray-json" % "1.3.2",
  "org.vaadin.addon" % "jfreechartwrapper" % "3.0.3" excludeAll ExclusionRule(organization = "javax.servlet"),
  "org.scalatest" %% "scalatest" % "2.2.6" % Test
)







// Settings for the Vaadin plugin widgetset compilation
// Widgetset compilation needs memory and to avoid an out of memory error it usually needs more memory:
//javaOptions in compileVaadinWidgetsets := Seq("-Xss8M", "-Xmx512M", "-XX:MaxPermSize=512M") //, "-Dgwt.usearchives=false")

//vaadinWidgetsets := Seq("NobuWidgetSet")

//vaadinOptions in compileVaadinWidgetsets := Seq("-logLevel", "DEBUG", "-strict")

// Compile widgetsets into the source directory (by default themes are compiled into the target directory)
//target in compileVaadinWidgetsets := (baseDirectory).value / "WebContent" / "VAADIN" / "widgetsets"

mappings in Universal ++= directory("WebContent")

scriptClasspath := Seq("*")

mainClass in (Compile, run) := Some("sss.analysis.Main")
