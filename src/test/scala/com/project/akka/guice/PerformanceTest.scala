package com.project.akka.guice

import org.scalatest.FeatureSpecLike
import com.google.inject.Guice
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import javax.inject.Inject
import com.project.akka.guice.PerformanceTest._
import akka.pattern.ask
import com.google.inject.util.Modules
import scala.concurrent.Await

class PerformanceTest extends FeatureSpecLike {

  implicit val timeout = Timeout(10.seconds)

  feature("Create the instance with a Child injector") {
    scenario("No parameter") {
      val system = ActorSystem("test")
      val injector = Guice.createInjector(Modules.EMPTY_MODULE)
      val props = injector.getInstance(classOf[InjectedProps])
      val injectedTime = Await.result(system.actorOf(Props[Runner]) ? props(classOf[NoParamsActor]), 10.seconds)
      println("No Parameter. Injected " + injectedTime)
      val nativeTime = Await.result(system.actorOf(Props[Runner]) ? Props(classOf[NoParamsActor]), 10.seconds)
      println("No Parameter. Native " + nativeTime)
    }
    scenario("Parameter") {
      val system = ActorSystem("test")
      val injector = Guice.createInjector(Modules.EMPTY_MODULE)
      val props = injector.getInstance(classOf[InjectedProps])
      val nativeTime = Await.result(system.actorOf(Props[Runner]) ? Props(classOf[ParamActor], ""), 10.seconds)
      println("Parameter. Native " + nativeTime)
      val injectedTime = Await.result(system.actorOf(Props[Runner]) ? props(classOf[ParamActor], ""), 10.seconds)
      println("Parameter. Injected " + injectedTime)
    }
  }
}

object PerformanceTest {

  val Cycle = 10000

  class Runner extends Actor {

    var propsSender: ActorRef = _
    var startTime: Long = 0
    var counter = 0

    def receive = {
      case props: Props =>
        propsSender = sender()
        startTime = System.currentTimeMillis()
        counter = 0
        (1 to Cycle).foreach( context.actorOf(props) ! _ )
      case _ =>
        counter += 1
        if (counter == Cycle) {
          val endTime = System.currentTimeMillis()
          propsSender ! (endTime - startTime)
        }
    }

  }

  class NoParamsActor extends Actor {
    def receive = { case message => sender() ! message }
  }

  class ParamActor @Inject() (hello: String) extends NoParamsActor

}

