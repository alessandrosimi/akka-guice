/**
 * Copyright 2015 Alessandro Simi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.project.akka.guice

import com.google.inject.{Key, Injector, Guice, Module}
import akka.actor._
import scala.concurrent.Await
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import org.scalatest.Assertions
import com.google.inject.name.Names
import com.project.akka.guice.Behaviour.{Given, When, Then}
import akka.actor.SupervisorStrategy.Stop
import scala.reflect.ClassTag

trait Behaviour {
  val given = new Given
  val when = new When
  val then = new Then
}

object Behaviour {

  private implicit val timeout = Timeout(2.seconds)

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
    def the(system: ActorSystem) = new WhenActorSystem(system)
    def a_message_is_sent_to(actorRef: ActorRef) = Await.result((actorRef ? "").mapTo[String], 2.seconds)
    def the(injector: Injector) = new WhenInjector(injector)
  }
  class WhenActorSystem(system: ActorSystem) {
    def create_the_actor_with(props: Props) = system.actorOf(Props(classOf[Supervisor], props))
  }
  class WhenInjector(injector: Injector) {
    def gets_the_injected_props = injector.getInstance(classOf[InjectedProps])
    def gets_the_string_named(name: String) = injector.getInstance(Key.get(classOf[String], Names.named(name)))
  }

  // Then
  class Then {
    def the(value: String) = new ThenValue(value)
    def the(exception: ActorInitializationException) = new ThenException(exception)
    def the(actor: ActorRef) = new ThenActor(actor)
  }
  class ThenValue(actual: String) extends Assertions {
    def should_be(expected: String) = assert(actual === expected)
    def should_contains(expected: String) = assert(actual.contains(expected))
  }
  class ThenActor(actor: ActorRef) extends Assertions {
    def should_be_not_started = {
      val response = Await.result(actor ? "", 2.seconds)
      assert(response.isInstanceOf[ActorInitializationException])
      response.asInstanceOf[ActorInitializationException]
    }
  }
  class ThenException(exception: ActorInitializationException) extends Assertions {
    def should_be_caused_by[T <: Exception : ClassTag]: String = {
      assert(exception.getCause.getClass == implicitly[ClassTag[T]].runtimeClass)
      exception.getCause.getMessage
    }
  }

  // Supervisor
  private class Supervisor(props: Props) extends Actor with Stash {

    var actor: Option[ActorRef] = None
    var error: Option[Exception] = None

    override def preStart() = {
      context.actorOf(props) ! Identify("please")
    }

    override val supervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case exception: Exception =>
          self ! exception
          Stop
      }

    def receive = {
      case exception: Exception =>
        error = Some(exception)
        unstashAll()
      case ActorIdentity(_, actorRef) =>
        actor = actorRef
        unstashAll()
      case message: String =>
        if (actor.isDefined) actor.get forward message
        else if (error.isDefined) sender() ! error.get
        else stash()
    }

  }
}