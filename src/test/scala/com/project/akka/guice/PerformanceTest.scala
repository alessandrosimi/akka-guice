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
      val request = Request(Props(classOf[NoParamsActor]), props(classOf[NoParamsActor]))
      val time = Await.result((system.actorOf(Props[BothRunner]) ? request).mapTo[Result], 10.seconds)
      println("No Parameter:" + time)
    }
    scenario("Parameter") {
      val system = ActorSystem("test")
      val injector = Guice.createInjector(Modules.EMPTY_MODULE)
      val props = injector.getInstance(classOf[InjectedProps])
      val request = Request(Props(classOf[ParamActor], ""), props(classOf[ParamActor], ""))
      val time = Await.result((system.actorOf(Props[BothRunner]) ? request).mapTo[Result], 10.seconds)
      println("Parameter:" + time)
    }
  }
}

object PerformanceTest {

  val Cycles = 10000
  
  class BothRunner extends Actor {
    
    var propsSender: ActorRef = _
    
    val nativeRunner = context.actorOf(Props[Runner])
    val injectedRunner = context.actorOf(Props[Runner])
    
    var nativeTime: Long = 0
    var injectedTime: Long = 0
    
    def receive = {
      case Request(native, injected) =>
        nativeRunner ! SingleRequest(native, true)
        injectedRunner ! SingleRequest(injected, false)
        nativeTime = 0
        injectedTime = 0
        propsSender = sender()
      case NativeResult(time) =>
        nativeTime = time
        sendResultIfComplete()
      case InjectedResult(time) =>
        injectedTime = time
        sendResultIfComplete()
    }


    def sendResultIfComplete() = if (nativeTime != 0 && injectedTime != 0)
      propsSender ! Result(nativeTime, injectedTime)
    
  }

  case class NativeResult(time: Long)
  case class InjectedResult(time: Long)
  
  class Runner extends Actor {

    var propsSender: ActorRef = _
    var time: Long = 0
    var counter = 0
    var isNative = true
    
    def receive = {
      case SingleRequest(props, native) =>
        propsSender = sender()
        isNative = native
        time = 0
        counter = 0
        (1 to Cycles).foreach ( number => context.actorOf(props) ! Counter() )
      case response: Counter =>
        counter += 1
        time += response.duration
        if (counter == Cycles) {
          propsSender ! (if (isNative) NativeResult(time) else InjectedResult(time))
        }
    }

  }
  
  def time = System.currentTimeMillis()

  class NoParamsActor extends Actor {
    def receive = { case message => sender() ! message }
  }

  class ParamActor @Inject() (hello: String) extends NoParamsActor
  
  case class Request(nativeProps: Props, injectedProps: Props)
  case class SingleRequest(props: Props, native: Boolean)
  
  case class Counter() {
    val start = time
    def duration = time - start
  }
  
  case class Result(native: Long, injected: Long) {
    override def toString: String = s"""
      |Native:   total ${native/1000} sec
      |          each  ${native/Cycles} mills
      |Injected: total ${injected/1000} sec
      |          each  ${injected/Cycles} mills""".stripMargin
  }
  
}

