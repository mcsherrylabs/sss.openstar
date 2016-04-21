package sss.asado.util

import javax.servlet.Servlet

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}



/**
  * Created by alan on 4/20/16.
  */
case class InitServlet(servletCls: Servlet, path: String)

object ServerLauncher {
  def apply(servletClasses : InitServlet*) : ServerLauncher = new ServerLauncher(7676, "/", "./", servletClasses: _*)
}

class ServerLauncher(port: Int, contextPath: String, resourceBase: String, servletClasses : InitServlet*)  {

  val server: Server = new Server(port)
  private val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS)

  context.setContextPath(contextPath)
  context.setResourceBase(resourceBase)
  server.setHandler(context)

  servletClasses foreach { init => context.addServlet(new ServletHolder(init.servletCls), init.path) }

  server.setGracefulShutdown(3000)
  server.setStopAtShutdown(true)

  def stop = server.stop

  def start = server.start


}
