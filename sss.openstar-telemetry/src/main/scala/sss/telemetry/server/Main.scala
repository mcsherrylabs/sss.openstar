package sss.telemetry.server

import akka.actor.ActorSystem
import sss.ancillary.Configure

object Main extends Configure {

  def main(args: Array[String]): Unit = {

    implicit val as = ActorSystem("telemetry")

    new TelemetryTLSWebSocketServer(
      config.getString("keyStorePassword"),
      config.getString("keyStorePath"),
      config.getString("bindToIp"),
      config.getInt("bindToPort"),
      config.getString("keyStoreType"),
      config.getString("keyFactoryType")
    )
  }



}

