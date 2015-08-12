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

import org.scalatest.FeatureSpecLike
import com.google.inject.{Scopes, CreationException, Provides, AbstractModule}
import akka.actor.Actor
import javax.inject.{Inject, Singleton, Named}
import com.project.akka.guice.InjectedPropsTest._

class InjectedPropsTest extends FeatureSpecLike with Behaviour {

  feature("Be able to create actor with no parameters constructor") {
    scenario("Scala API") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props(classOf[NoParameterActor]))
      val response = when.a_message_is_sent_to(ref)
      val responseValue = when.the(injector).gets_the_string_named(Response)
      then.the(response).should_be(responseValue)
    }
    scenario("Scala API with class tag") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props[NoParameterActor])
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
  feature("Be able to create actor with a constructor with parameter") {
    scenario("Two parameters, only one is injected") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props(classOf[ParameterActor], "after"))
      val response = when.a_message_is_sent_to(ref)
      val responseValue = when.the(injector).gets_the_string_named(Response)
      then.the(response).should_be(responseValue + "after")
    }
    scenario("Two non injected parameters with same type and one with Java annotation") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props(classOf[ActorWithTwoStringParametersWithAnnotation], "one", "two"))
      val response = when.a_message_is_sent_to(ref)
      then.the(response).should_be("onetwo")
    }
    scenario("Two non injected parameters with same type and one with Guice annotation") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val ref = when.the(system).create_the_actor_with(props(classOf[ActorWithTwoStringParametersWithGuiceAnnotation], "one", "two"))
      val response = when.a_message_is_sent_to(ref)
      then.the(response).should_be("onetwo")
    }
    scenario("Two non injected parameters with same type and no annotations") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[ActorWithTwoStringParametersWithoutAnnotation], "one", "two"))
      val error = then.the(actor).should_be_not_started
      val errorMessage = then.the(error).should_be_caused_by[CreationException]
      then.the(errorMessage).should_contains("String was already configured")
    }
    scenario("Too many non injected parameters") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[ParameterActor], "one", "two"))
      val error = then.the(actor).should_be_not_started
      val errorMessage = then.the(error).should_be_caused_by[CreationException]
      then.the(errorMessage).should_contains("not injected parameter")
    }
  }
  feature("Not be able to create an actor with a singleton scope") {
    scenario("Singleton scope actor") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[SingletonActor]))
      val error = then.the(actor).should_be_not_started
      val errorMessage = then.the(error).should_be_caused_by[CreationException]
      then.the(errorMessage).should_contains("singleton scope")
    }
    scenario("Singleton annotated actor with Java annotation") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[JavaSingletonAnnotatedActor]))
      val error = then.the(actor).should_be_not_started
      val errorMessage = then.the(error).should_be_caused_by[CreationException]
      then.the(errorMessage).should_contains("singleton scope")
    }
    scenario("Singleton annotated actor with Guice annotation") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[GuiceSingletonAnnotatedActor]))
      val error = then.the(actor).should_be_not_started
      val errorMessage = then.the(error).should_be_caused_by[CreationException]
      then.the(errorMessage).should_contains("singleton scope")
    }
    scenario("Only bound actor") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[BoundActor]))
      val response = when.a_message_is_sent_to(actor)
      then.the(response).should_be("")
    }
  }
  feature("Be able to create an actor with generic parameter constructor") {
    scenario("One generic parameter") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[ActorWithOneGenericParameter], List("one")))
      val response = when.a_message_is_sent_to(actor)
      then.the(response).should_be("one")
    }
    scenario("Two generic parameters") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[ActorWithTwoGenericParameters], List("one"), List(2)))
      val response = when.a_message_is_sent_to(actor)
      then.the(response).should_be("one2")
    }
    scenario("Fail with two generic parameters of same type") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[ActorWithTwoGenericParametersOfSameType], List("one"), List("two")))
      val error = then.the(actor).should_be_not_started
      val errorMessage = then.the(error).should_be_caused_by[CreationException]
      then.the(errorMessage).should_contains("was already configured")
    }
  }
  feature("Be able to create an actor passing sub-type parameters") {
    scenario("Using subtype parameter should pass") {
      val injector = given.an_injector.with_module(new Module)
      val system = given.an_actor_system
      val props = when.the(injector).gets_the_injected_props
      val actor = when.the(system).create_the_actor_with(props(classOf[ActorWithSubTypeParameter], List("one")))
      val response = when.a_message_is_sent_to(actor)
      then.the(response).should_be("one")
    }
  }

}

object InjectedPropsTest {

  final val Response = "response"

  class Module extends AbstractModule {
    def configure() = {
      bind(classOf[SingletonActor]).in(Scopes.SINGLETON)
    }
    @Provides @Named(Response) @Singleton
    def response = System.currentTimeMillis().toString
  }

  class NoParameterActor @Inject() (@Named(Response) val response: String) extends Actor {
    def receive = { case _ => sender() ! response }
  }

  class ParameterActor @Inject() (@Named(Response) val response: String, val suffix: String) extends Actor {
    def receive = { case _ => sender() ! response + suffix }
  }

  class ActorWithTwoStringParametersWithAnnotation @Inject() (@Named("one") val one: String, val two: String) extends Actor {
    def receive = { case _ => sender() ! one + two }
  }

  class ActorWithTwoStringParametersWithGuiceAnnotation @com.google.inject.Inject() (@com.google.inject.name.Named("one") val one: String, val two: String) extends Actor {
    def receive = { case _ => sender() ! one + two }
  }

  abstract class EmptyActor extends Actor {
    def receive = { case _ => sender() ! "" }
  }

  class ActorWithTwoStringParametersWithoutAnnotation @Inject() (val one: String, val two: String) extends EmptyActor

  @javax.inject.Singleton
  class JavaSingletonAnnotatedActor extends EmptyActor

  @com.google.inject.Singleton
  class GuiceSingletonAnnotatedActor extends EmptyActor

  class SingletonActor extends EmptyActor

  class BoundActor extends EmptyActor

  class ActorWithOneGenericParameter @Inject() (one: List[String]) extends Actor {
    def receive = { case _ => sender() ! one(0) }
  }

  class ActorWithTwoGenericParameters @Inject() (one: List[String], two: List[Integer]) extends Actor {
    def receive = { case _ => sender() ! one(0) + two(0) }
  }

  class ActorWithTwoGenericParametersOfSameType @Inject() (one: List[String], two: List[String]) extends Actor {
    def receive = { case _ => sender() ! one(0) + two(0) }
  }

  class ActorWithSubTypeParameter @Inject() (one: Seq[String]) extends Actor {
    def receive = { case _ => sender() ! one(0) }
  }

}