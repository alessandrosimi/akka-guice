package com.project.akka.guice

import org.scalatest.FeatureSpecLike
import com.google.inject.{Provides, AbstractModule}
import akka.actor.Actor
import javax.inject.{Singleton, Named, Inject}

class InjectedPropsTest extends FeatureSpecLike with Behaviour {

  feature("Create actor with no parameters constructor") {
    scenario("Scala API") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props(classOf[NoParameterActor]))
      val response = when.a_message_is_sent_to(ref)
      val responseValue = when.the(injector).gets_the_string_named(Response)
      then.the(response).should_be(responseValue)
    }
    scenario("Java API") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props.create(classOf[NoParameterActor]))
      val response = when.a_message_is_sent_to(ref)
      val responseValue = when.the(injector).gets_the_string_named(Response)
      then.the(response).should_be(responseValue)
    }
  }
  feature("Create actor with a constructor with parameter") {
    scenario("Two parameters, only one is injected") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props(classOf[ParameterActor], "after"))
      val response = when.a_message_is_sent_to(ref)
      val responseValue = when.the(injector).gets_the_string_named(Response)
      then.the(response).should_be(responseValue + "after")
    }
    scenario("Two non injected parameters with same type and one with annotation") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props(classOf[ActorWithTwoStringParametersWithAnnotation], "one", "two"))
      val response = when.a_message_is_sent_to(ref)
      then.the(response).should_be("onetwo")
    }
    scenario("Two non injected parameters with same type and no annotations") { // TODO Create the actor with another actor to catch the initialization exception
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props(classOf[ActorWithTwoStringParametersWithoutAnnotation], "one", "two"))
    }
  }
}

object InjectedPropsTest {

  final val Response = "response"

  class Module extends AbstractModule {
    def configure() = {}
    @Provides @Named(Response) @Singleton
    def response = System.currentTimeMillis().toString
  }

  class NoParameterActor @Inject() (@Named(Response) val response: String) extends Actor {
    def receive = {
      case _ => sender() ! response
    }
  }

  class ParameterActor @Inject() (@Named(Response) val response: String, val suffix: String) extends Actor {
    def receive = {
      case _ => sender() ! response + suffix
    }
  }

  class ActorWithTwoStringParametersWithAnnotation @Inject() (@Named("one") val one: String, val two: String) extends Actor {
    def receive = {
      case _ => sender() ! one + two
    }
  }

  class ActorWithTwoStringParametersWithoutAnnotation @Inject() (val one: String, val two: String) extends Actor {
    def receive = {
      case _ => sender() ! one + two
    }
  }

}