package sss.asado.network

import java.net.{NetworkInterface, InetAddress, URI}

import sss.ancillary.Logging
import scala.collection.JavaConversions._
import scala.util.Try

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/11/16.
  */
object NetworkUtil extends Logging {

  def isAddressValid(declaredAddress: Option[String], upnpOpt: Option[UPnP] = None): Boolean = {
    //check own declared address for validity

    declaredAddress.map { myAddress =>
      Try {
        val uri = new URI("http://" + myAddress)
        val myHost = uri.getHost
        val myAddrs = InetAddress.getAllByName(myHost)

        NetworkInterface.getNetworkInterfaces.exists { intf =>
          intf.getInterfaceAddresses.exists { intfAddr =>
            val extAddr = intfAddr.getAddress
            myAddrs.contains(extAddr)
          }
        } match {
          case true => true
          case false => upnpOpt.map { upnp =>
            val extAddr = upnp.externalAddress
            myAddrs.contains(extAddr)
          }.getOrElse(false)
        }
      }.recover { case t: Throwable =>
        log.error("Declared address validation failed: ", t)
        false
      }.getOrElse(false)
    }.getOrElse(true).ensuring(_ == true, "Declared address isn't valid")
  }

}