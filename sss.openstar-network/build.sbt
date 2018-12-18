name := "sss.openstar-network"

parallelExecution in Test := false

libraryDependencies += "com.typesafe.akka" %% "akka-testkit"  % Vers.akkaVer % Test

libraryDependencies += "org.scalactic" %% "scalactic" % Vers.scalacticVer

libraryDependencies += "org.consensusresearch" %% "scrypto" % Vers.scryptoVer

libraryDependencies += "commons-net" % "commons-net" % "3.+"

libraryDependencies += "com.mcsherrylabs" %% "sss-ancillary" % Vers.ancillaryVer

libraryDependencies += "com.typesafe.akka" %% "akka-actor" %  Vers.akkaVer

libraryDependencies += "com.typesafe.akka" %% "akka-agent" %  Vers.akkaVer

libraryDependencies += "org.bitlet" % "weupnp" % "0.1.+"


