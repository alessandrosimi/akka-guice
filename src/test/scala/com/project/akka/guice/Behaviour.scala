package com.project.akka.guice

import com.google.inject.{Key, Injector, Guice, Module}
import akka.actor.{ActorRef, Props, ActorSystem}
import com.project.akka.guice.InjectedProps
import scala.concurrent.Await
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import org.scalatest.Assertions
import com.google.inject.name.Names

trait Behaviour {
  val given = new Given
  val when = new When
  val then = new Then
}

object Behaviour {

  // Given
  class Given {
    val an_injector = new GivenInjector
    val an_actor_system = ActorSystem("actor-system")
  }
  class GivenInjector {
    def with_module(module: Module) =
      Guice.createInjector(module)
  }

  // When
  class When {
    private implicit val timeout = Timeout(2.seconds)
    def the(system: ActorSystem) = new WhenActorSystem(system)
    def a_message_is_sent_to(actorRef: ActorRef) = Await.result(actorRef ? "", 2.seconds)
    def the(injector: Injector) = new WhenInjector(injector)
  }
  class WhenActorSystem(system: ActorSystem) {
    def create_the_actor_with(props: Props) = system.actorOf(props)
  }
  class WhenInjector(injector: Injector) {
    def gets_the_injected_props = injector.getInstance(classOf[InjectedProps])
    def gets_the_string_named(name: String) = injector.getInstance(Key.get(classOf[String], Names.named(name)))
  }

  // Then
  class Then {
    def the(value: Any) = new ThenValue(value)
  }
  class ThenValue(actual: Any) extends Assertions {
    def should_be(expected: String) = assert(actual === expected)
  }
}