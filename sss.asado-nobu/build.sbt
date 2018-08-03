import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.Keys.{maintainer, packageDescription, packageSummary}
import com.typesafe.sbt.packager.windows.{AddShortCuts, ComponentFile, WindowsFeature, WindowsProductInfo, WixHelper}
import java.util.regex.Pattern

enablePlugins(JDKPackagerPlugin, WindowsPlugin)

packageSummary in Linux := "nobu-openstar"

resolvers += "vaadin-addons" at "http://maven.vaadin.com/vaadin-addons"

resolvers += "indvd00m-github-repo" at "https://github.com/indvd00m/maven-repo/raw/master/repository"

resolvers += "Sonatype Nexus Releases" at "https://oss.sonatype.org/content/repositories/releases"

//Seq(vaadinWebSettings: _*)

val vaadinVer = "7.7.13"

coverageEnabled := false

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

jdkPackagerType := "image"

mappings in Universal ++= directory(baseDirectory.value / "WebContent")

(name in JDKPackager) := "openstar"

// Cannot figure out another way to make the windows installer valid.
(version in JDKPackager):= version.value.replaceAll("-SNAPSHOT", "")
(version in Windows):= version.value.replaceAll("-SNAPSHOT", "")

mainClass in (Compile, run) := Some("sss.ui.nobu.Main")

lazy val iconGlob = sys.props("os.name").toLowerCase match {
  case os if os.contains("mac") ⇒ "*.icns"
  case os if os.contains("win") ⇒ "*.ico"
  case _ ⇒ "*.png"
}

maintainer := "Stepping Stone Software Ltd."
packageSummary := "nobu openstar"
packageDescription := "Nobu Openstar Install"

// wix build information
wixProductId := "dd41efe9-b0e8-426f-8ce3-5270e631032f"
wixProductUpgradeId := "4e7cec34-0c58-4b2d-8392-8b69aaccd743"
wixProductLicense := Option(baseDirectory.value / "License.rtf")

val sep = java.io.File.separator

jdkPackagerJVMArgs := Seq(
  "-Dconfig.file=." + sep + "conf" + sep + "application.conf",
  "-Dlogback.configurationFile=." + sep + "conf" + sep + "logback.xml",
  "-Xss10M"
)

jdkAppIcon :=  ((resourceDirectory in Compile).value ** iconGlob).getPaths.headOption.map(file)

mappings in Windows :=  directory(target.value / "universal" / "jdkpackager" / "bundles" / "openstar" )
  .map(fs => (fs._1, fs._2.replaceFirst("openstar" + Pattern.quote(sep), "")))
  .filterNot(fs => fs._2.isEmpty || fs._2 == "openstar")

lazy val editableFileExtensions = Seq(".xml", ".conf", ".cfg", ".ini", ".scss")
lazy val confFolderPrefix = "app" +  Pattern.quote(sep) + "conf" + Pattern.quote(sep)

def isInstalledFileEditable(name: String): Boolean = editableFileExtensions.exists(name.endsWith(_))

wixPackageInfo := WindowsProductInfo(
  id = wixProductId.value,
  title = (packageSummary in Windows).value,
  version = (version in Windows).value,
  maintainer = (maintainer in Windows).value,
  description = (packageDescription in Windows).value,
  upgradeId = wixProductUpgradeId.value,
  comments = "Nobu Openstar service install (openstar.io)")


wixFeatures := {
  val files =
    for {
      (file, name) <- (mappings in Windows).value
      if !file.isDirectory
    } yield ComponentFile(name, isInstalledFileEditable(name), false)
  val corePackage =
    WindowsFeature(
      id = WixHelper.cleanStringForId(name + "_core").takeRight(38), // Must be no longer
      title = (packageName in Windows).value,
      desc = "All core files.",
      absent = "disallow",
      components = files
    )
  val configLinks = for {
    (file, name) <- (mappings in Windows).value
    if (".exe" +: editableFileExtensions).exists(name.endsWith(_))
  } yield name.replaceAll("//", "/").stripSuffix("/").stripSuffix("/")
  val menuLinks =
    WindowsFeature(
      id = "AddConfigLinks",
      title = "Configuration start menu links",
      desc = "Adds start menu shortcuts to edit configuration files.",
      components = Seq(AddShortCuts(configLinks))
    )

  Seq(corePackage, menuLinks)
}