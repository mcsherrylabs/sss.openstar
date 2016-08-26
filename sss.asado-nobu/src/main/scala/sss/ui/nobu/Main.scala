package sss.ui.nobu

import sss.ancillary.{DynConfig, _}

/**
  * Created by alan on 6/10/16.
  */
object Main {

  var server :ServerLauncher = _

  def main(args: Array[String]) {

    val httpConfig = DynConfig[ServerConfig]("httpServerConfig")
    server = ServerLauncher(httpConfig,
      ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")),
      ServletContext("/service", ""))

    server.start
    server.join
  }

  def buildUIServlet = new sss.ui.Servlet

}

