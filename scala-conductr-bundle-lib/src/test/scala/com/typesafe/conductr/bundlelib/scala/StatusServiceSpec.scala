package com.typesafe.conductr.bundlelib.scala

import com.typesafe.conductr.AkkaUnitTest
import com.typesafe.conductr.bundlelib.scala.ConnectionContext.Implicits
import scala.concurrent.Await

class StatusServiceSpec extends AkkaUnitTest("StatusServiceSpec", "akka.loglevel = INFO") {

  import Implicits.global

  "The StatusService functionality in the library" should {
    "return None when running in development mode" in {
      Await.result(StatusService.signalStartedOrExit(), timeout.duration) shouldBe None
      Await.result(StatusService.signalStarted(), timeout.duration) shouldBe None
    }
  }
}
