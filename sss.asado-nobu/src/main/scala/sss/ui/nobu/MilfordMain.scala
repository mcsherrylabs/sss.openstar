package sss.ui.nobu


import sss.ancillary. _
import sss.ui.GenericUIProvider


/**
  * Created by alan on 6/10/16.
  */
object MilfordMain {


  def main(withArgs: Array[String]) {

    val provider = GenericUIProvider(classOf[MilfordUI], _ => new MilfordUI)

    val httpConfig = DynConfig[ServerConfig]("httpServerConfig")

    def buildUIServlet = new sss.ui.Servlet(provider)

    val httpServer = ServerLauncher(httpConfig,
      ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")))

    httpServer.start
    httpServer.join

  }
}

