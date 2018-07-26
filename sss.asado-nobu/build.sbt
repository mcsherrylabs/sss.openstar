import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._

enablePlugins(JavaAppPackaging)

packageSummary in Linux := "asado-nobu"

resolvers += "vaadin-addons" at "http://maven.vaadin.com/vaadin-addons"

resolvers += "indvd00m-github-repo" at "https://github.com/indvd00m/maven-repo/raw/master/repository"

resolvers += "Sonatype Nexus Releases" at "https://oss.sonatype.org/content/repositories/releases"

//Seq(vaadinWebSettings: _*)

val vaadinVer = "7.7.13"


//dependencyOverrides += "com.mcsherrylabs" %% "sss-ancillary" % "1.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % Vers.akkaVer,
  "com.typesafe.akka" %% "akka-remote" % Vers.akkaVer,
  "com.vaadin" % "vaadin-server" % vaadinVer,
  "com.vaadin" % "vaadin-themes" % vaadinVer,
  "com.vaadin" % "vaadin-push" % vaadinVer,
  "com.vaadin" % "vaadin-client-compiler" % vaadinVer,
  "com.vaadin" % "vaadin-client-compiled" % vaadinVer,
  "us.monoid.web" % "resty" % Vers.restyVer,
  "org.vaadin.icons" % "vaadin-icons" % "1.0.1",
  "org.vaadin.addons" % "animator" % "1.7.4",
  "com.mcsherrylabs" %% "sss-vaadin-akka-reactive" % Vers.sssVaadinReact,
  "org.scalatra" %% "scalatra" % Vers.scalatraVer,
  "io.spray" %%  "spray-json" % Vers.sprayJsonVer,
  "com.typesafe.akka" %% "akka-slf4j" % Vers.akkaVer
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

mainClass in (Compile, run) := Some("sss.ui.nobu.Main")
