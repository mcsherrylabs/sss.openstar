package sss.asado.util

import java.io.File
import java.net.InetSocketAddress
import javax.servlet.Servlet

import org.eclipse.jetty.server.ssl.SslSocketConnector
import org.eclipse.jetty.server.{Connector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.ssl.SslContextFactory



/**
  * Created by alan on 4/20/16.
  */
case class InitServlet(servletCls: Servlet, path: String)

trait ServerConfig {
  val contextPath: String
  val resourceBase: String
  val httpPort: Int
  val httpsPort: Int
  val useHttpConnector: Boolean
  val useSslConnector: Boolean
  val clientMustAuthenticate : Boolean
  val keyStoreLocation : String
  val keyStorePass :String
  val trustStoreLocation : String
  val trustStorePass :String
  val gracefulShutdownMs: Int
  val hostAddressOpt: Option[String]
}

case class DefaultServerConfig(contextPath: String = "/",
 resourceBase: String =  "./",
 httpPort: Int = 8080,
 httpsPort: Int = 8443,
 useHttpConnector: Boolean = true,
 useSslConnector: Boolean = false,
 clientMustAuthenticate: Boolean = false, // client certs NOT tested as of May 2016,
 keyStoreLocation: String = "",
 keyStorePass: String = "",
 trustStoreLocation: String = "",
 trustStorePass: String = "",
 gracefulShutdownMs: Int = 6000,
 hostAddressOpt: Option[String] = None) extends ServerConfig


object ServerLauncher {
  def apply(serverConfig: ServerConfig, servletClasses : InitServlet*) : ServerLauncher = new ServerLauncher(serverConfig, servletClasses: _*)
  def apply(port: Int, servletClasses : InitServlet*) : ServerLauncher = new ServerLauncher(DefaultServerConfig().copy(httpPort = port), servletClasses: _*)
}

class ServerLauncher(serverConfig: ServerConfig, servletClasses : InitServlet*)  {

  import serverConfig._
  private val server: Server = new Server(makeInetAddress)
  private val context = createContext

  servletClasses foreach { init => context.addServlet(new ServletHolder(init.servletCls), init.path) }

  server.setGracefulShutdown(gracefulShutdownMs)
  server.setStopAtShutdown(true)

  (useHttpConnector, useSslConnector) match {
    case (true, true) => server.addConnector(createSslConnector)
    case (false, true) => server.setConnectors(Array(createSslConnector))
    case (true, false) =>
    case (false, false) => throw new IllegalArgumentException("Use of both ssl and http connectors are false, at least one must be used")
  }

  server.setHandler(context)

  def stop = server.stop

  def start = server.start

  private def makeInetAddress: InetSocketAddress = {
    hostAddressOpt match {
      case Some(host) => InetSocketAddress.createUnresolved(host, httpPort)
      case None => new InetSocketAddress(httpPort)
    }
  }

  private def createContext: ServletContextHandler = {
    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath(contextPath)
    context.setResourceBase(resourceBase)
    context
  }

  private def createSslConnector: Connector = {
    require(new File(keyStoreLocation).isFile, s"Key store location ($keyStoreLocation) must exist and be a file.")
    val sslContextFactory = new SslContextFactory(keyStoreLocation)

    require(Option(keyStorePass).isDefined && keyStorePass.length > 0, "The password may not be empty or null.")
    sslContextFactory.setKeyStorePassword(keyStorePass)
    require(new File(trustStoreLocation).isFile, s"Trust store location ($trustStoreLocation) must exist and be a file.")
    sslContextFactory.setTrustStore(trustStoreLocation)

    require(Option(trustStorePass).isDefined && trustStorePass.length > 0, "The password may not be empty or null.")
    sslContextFactory.setTrustStorePassword(trustStorePass)
    sslContextFactory.setNeedClientAuth(clientMustAuthenticate)

    // create a https connector
    val connector = new SslSocketConnector(sslContextFactory)
    connector.setPort(httpsPort)
    connector
  }


}
