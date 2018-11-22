
val vaadinVer = Vers.vaadinVer

coverageEnabled := false

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % Vers.akkaVer,
  "com.typesafe.akka" %% "akka-remote" % Vers.akkaVer,
  "com.vaadin" % "vaadin-server" % vaadinVer,
  "com.vaadin" % "vaadin-themes" % vaadinVer,
  "com.vaadin" % "vaadin-push" % vaadinVer,
  "com.vaadin" % "vaadin-client-compiler" % vaadinVer,
  "com.vaadin" % "vaadin-client-compiled" % vaadinVer,
  "us.monoid.web" % "resty" % Vers.restyVer,
  "com.mcsherrylabs" %% "sss-vaadin-akka-reactive" % Vers.sssVaadinReact,
  "org.scalatra" %% "scalatra" % Vers.scalatraVer,
  "io.spray" %%  "spray-json" % Vers.sprayJsonVer,
  "com.typesafe.akka" %% "akka-slf4j" % Vers.akkaVer,
  "ch.qos.logback" % "logback-classic" % "1.0.0" % "runtime"
)


// Settings for the Vaadin plugin widgetset compilation
// Widgetset compilation needs memory and to avoid an out of memory error it usually needs more memory:
//javaOptions in compileVaadinWidgetsets := Seq("-Xss8M", "-Xmx512M", "-XX:MaxPermSize=512M") //, "-Dgwt.usearchives=false")

//vaadinWidgetsets := Seq("NobuWidgetSet")

//vaadinOptions in compileVaadinWidgetsets := Seq("-logLevel", "DEBUG", "-strict")

// Compile widgetsets into the source directory (by default themes are compiled into the target directory)
//target in compileVaadinWidgetsets := (baseDirectory).value / "WebContent" / "VAADIN" / "widgetsets"


