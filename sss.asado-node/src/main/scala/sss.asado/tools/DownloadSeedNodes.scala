package sss.asado.tools

import java.net.InetSocketAddress

import sss.ancillary.Logging
import sss.asado.network.NodeId
import us.monoid.web.Resty

import scala.util.{Failure, Success, Try}

object DownloadSeedNodes extends Logging {

  def get(url: String): Try[String] = Try(new Resty().text(url)).recover {
    case e:Exception => log.warn(e.getMessage); throw e
  } map (_.toString)

  private def toInetSocketAddress(hostName: String, port:String): InetSocketAddress =
    new InetSocketAddress(hostName, port.toInt)

  def download(url: String): Set[NodeId] = {

    get(url) map {
      _.split("::")
        .map (_.split(":"))
        .map (ary => Try(NodeId(ary(0), toInetSocketAddress(ary(1), ary(2)))))
        .collect { case Success(n) => n }
        .toSet
    } match {
      case Success(s) =>
        s
      case Failure(e) =>
        log.warn(e.toString)
        Set.empty
    }

  }

}
