name := "sss.asado-ledger"

parallelExecution in Test := false

libraryDependencies += "com.mcsherrylabs" %% "sss-ancillary" % Vers.ancillaryVer

libraryDependencies += "com.mcsherrylabs" %% "sss-db" % Vers.sssDbVer

libraryDependencies += "org.hsqldb" % "hsqldb" % Vers.hsqldbVer % Test
