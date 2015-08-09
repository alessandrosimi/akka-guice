package com.poject.akka.guice

import org.scalatest.FeatureSpecLike
import com.google.inject.{AbstractModule, Guice}
import com.poject.akka.guice.PerformanceTest.{ParamActor, NoParamsActor, Module}
import scala.concurrent.duration._
import akka.util.Timeout
import com.google.inject.util.Modules
import akka.actor.{Props, ActorSystem, Actor}
import javax.inject.{Named, Inject}
import com.google.inject.name.Names
import com.project.akka.guice.InjectedProps

class PerformanceTest extends FeatureSpecLike {

  implicit val timeout = Timeout(2.seconds)

  feature("Create the instance with a Child injector") {
    scenario("Direct") {
      val system = ActorSystem("test")
      val injector = Guice.createInjector(new Module)
      val props = injector.getInstance(classOf[InjectedProps])
      def direct = {
        var i = 0
        val start = System.currentTimeMillis()
        while(i < 100000) {
          i += 1
          system.actorOf(Props(classOf[NoParamsActor]))
        }
        val end = System.currentTimeMillis()
        println("Direct " + (end - start) + ", each is " + (end - start) / 10000)
      }
      direct
      def injected = {
        var i = 0
         val start = System.currentTimeMillis()
         while(i < 100000) {
           i += 1
           system.actorOf(props(classOf[NoParamsActor]))
         }
         val end = System.currentTimeMillis()
         println("Injected " + (end - start) + ", each is " + (end - start) / 10000)
      }
      injected
    }
    scenario("Child") {
      val system = ActorSystem("test")
      val injector = Guice.createInjector(new Module)
      val props = injector.getInstance(classOf[InjectedProps])
      def direct = {
        var i = 0
        val start = System.currentTimeMillis()
        while(i < 100000) {
          i += 1
          system.actorOf(Props(classOf[ParamActor], ""))
        }
        val end = System.currentTimeMillis()
        println("Param.Direct " + (end - start) + ", each is " + (end - start) / 10000)
      }
      direct
      def injected = {
        var i = 0
        val start = System.currentTimeMillis()
        while(i < 100000) {
          i += 1
          system.actorOf(props(classOf[ParamActor], ""))
        }
        val end = System.currentTimeMillis()
        println("Param.Injected " + (end - start) + ", each is " + (end - start) / 10000)
      }
      injected
    }
  }
}

object PerformanceTest {

  class Module extends AbstractModule {
    def configure() = {}
  }

  class NoParamsActor extends Actor {
    def receive: Actor.Receive = { case _ => }
  }

  class ParamActor @Inject() (hello: String) extends NoParamsActor

}

