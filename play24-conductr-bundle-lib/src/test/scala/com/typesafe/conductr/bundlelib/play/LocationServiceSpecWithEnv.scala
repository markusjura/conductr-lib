package com.typesafe.conductr.bundlelib.play

import java.net.{ URI => JavaURI, InetSocketAddress }

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ CacheDirectives, Location, `Cache-Control` }
import akka.http.scaladsl.model.{ HttpEntity, HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer
import akka.testkit.TestProbe
import com.typesafe.conductr.bundlelib.play.ConnectionContext.Implicits
import com.typesafe.conductr.bundlelib.scala.{ URL, URI, LocationCache }
import com.typesafe.conductr.{ AkkaUnitTest, _ }

import scala.concurrent.Await
import scala.util.{ Failure, Success }

class LocationServiceSpecWithEnv extends AkkaUnitTest("LocationServiceSpecWithEnv", "akka.loglevel = INFO") {

  import Implicits.defaultContext

  "The LocationService functionality in the library" should {

    "be able to look up a named service" in {
      val serviceUri = URI("http://service_interface:4711/known")
      withServerWithKnownService(serviceUri) {
        val cache = LocationCache()
        val service = LocationService.lookup("/known", URI(""), cache)
        Await.result(service, timeout.duration) shouldBe Some(serviceUri)
      }
    }
  }

  def withServerWithKnownService(serviceUri: JavaURI, maxAge: Option[Int] = None)(thunk: => Unit): Unit = {
    import system.dispatcher
    implicit val materializer = ActorFlowMaterializer.create(system)

    val probe = new TestProbe(system)

    val handler =
      path("services" / Rest) { serviceName =>
        get {
          complete {
            serviceName match {
              case "known" =>
                val headers = Location(serviceUri.toString) :: (maxAge match {
                  case Some(maxAgeSecs) =>
                    `Cache-Control`(
                      CacheDirectives.`private`(Location.name),
                      CacheDirectives.`max-age`(maxAgeSecs)) :: Nil
                  case None =>
                    Nil
                })
                HttpResponse(StatusCodes.TemporaryRedirect, headers, HttpEntity(s"Located at $serviceUri"))
              case _ =>
                HttpResponse(StatusCodes.NotFound)
            }
          }
        }
      }

    val url = URL(Env.serviceLocator.get)
    val server = Http(system).bindAndHandle(handler, url.getHost, url.getPort)

    try {
      server.onComplete {
        case Success(binding) => probe.ref ! binding.localAddress
        case Failure(e)       => probe.ref ! e
      }

      val address = probe.expectMsgType[InetSocketAddress]
      address.getHostString should be(url.getHost)
      address.getPort should be(url.getPort)

      thunk
    } finally {
      server.foreach(_.unbind())
    }
  }
}
