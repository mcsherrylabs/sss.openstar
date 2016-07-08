package sss.asado

import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, Suite}

/**
  * Created by alan on 4/1/16.
  */
trait StopSystemAfterAll extends BeforeAndAfterAll{
  this: TestKit with Suite =>
  override protected def afterAll() {
    super.afterAll()
    system.terminate
  }

}
