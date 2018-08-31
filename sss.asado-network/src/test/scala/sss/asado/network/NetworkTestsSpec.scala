package sss.asado.network

import org.scalatest.{FlatSpec, Matchers}

class NetworkTestsSpec extends FlatSpec with Matchers {


  "The network tests " should " run and print report " in {

    testserver.TestServerRunner.test(TestActorSystem.actorSystem)

  }
}
