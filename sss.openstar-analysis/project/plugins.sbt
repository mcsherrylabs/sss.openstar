// Vaadin SBT plugin
resolvers += "sbt-vaadin-plugin repo" at "http://henrikerola.github.io/repository/releases"

scalaVersion := "2.10.5"

/*resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
   url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
       Resolver.ivyStylePatterns)*/

//resolvers += "sbt-vaadin-plugin repo" at "https://github.com/henrikerola/henrikerola.github.io/tree/master/repository/releases"

// IDE plugin and others
resolvers += Classpaths.typesafeReleases


//addSbtPlugin("org.vaadin.sbt" % "sbt-vaadin-plugin" % "1.2.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.6")



