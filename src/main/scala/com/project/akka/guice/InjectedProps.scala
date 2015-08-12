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

import javax.inject.{Qualifier, Inject}
import com.google.inject._
import scala.annotation.varargs
import java.lang.reflect.Constructor
import java.lang.annotation.Annotation
import com.google.inject.spi.{DefaultBindingScopingVisitor, Message}
import akka.actor.{IndirectActorProducer, Props, Actor}
import scala.reflect.ClassTag

/** [[com.project.akka.guice.InjectedProps InjectedProps]] is used instead of [[akka.actor.Props Props]] when the
  * [[akka.actor.Actor Actor]] should be created inside the a [[com.google.inject.Guice Guice]] context.
  * {{{
  * // Scala API
  * &#64;Inject val props: InjectedProps = _
  * val actorRef = system.actorOf(props[MyActor])
  * // Java API
  * &#64;Inject InjectedProps props;
  * ActorRef actorRef = system.actorOf(props.create(MyActor.class));
  * }}}
  * The actor create can benefits of the injection and the AOP provided by Guice
  * {{{
  * // Scala API
  * class MyActor &#64;Inject() (service: MyService) { ... }
  * // Java API
  * public class MyActor {
  *   &#64;Inject
  *   public MyActor(MyService service) {
  *     this.service = service;
  *   }
  * }
  * }}}
  * It is possible to create the actor with parameters
  * {{{
  * // Scala API
  * val actorRef = system.actorOf(props(classOf[MyActor], "myParam"))
  * // Java API
  * ActorRef actorRef = system.actorOf(props.create(MyActor.class, "myParam"));
  * }}}
  * This API can be used within explicit parameters at the same time of injected parameters
  * {{{
  * // Scala API
  * class MyActor &#64;Inject() (service: MyService, myParam: String) { ... }
  * // Java API
  * public class MyActor {
  *   &#64;Inject
  *   public MyActor(MyService service, String myParam) {
  *     this.service = service;
  *     this.myParam = myParam;
  *   }
  * }
  * }}}
  * There are only two restrictions:<br/>
  * 1. The injected parameters should be bound explicitly (not JIT - Just In Time) binding.<br/>
  * 2. The passed parameter should have different binding type (or Guice Key).
  * {{{
  * // Good
  * MyActor(String one, &#64;Named("two") String two) { ... }
  * MyActor(List<String> one, List<Integer> two) { ... }
  * MyActor(MyService myService, String myParam) { ... } // with bind(MyService.class).to( ... );
  * // Bad
  * MyActor(String one, String two) { ... }
  * MyActor(List<String> one, List<String> two) { ... }
  * MyActor(MyService myService, String myParam) { ... } // without explicit binding
  * }}}
  * There is a validation check to block the creation of the bounded actor with '''singleton''' scope.
  * @author alesssandro.simi
  * @see [[akka.actor.Actor Actor]], [[akka.actor.Props Props]], [[com.google.inject.Guice Guice]]
  */
@Singleton
class InjectedProps @Inject() (injector: Injector) {

  /**
   * Scala API: create a Props given a class and its constructor arguments.
   */
  def apply(clazz: Class[_ <: Actor], args: Any*): Props = createProps(clazz, args.toList)

  /**
   * Scala API: create a Props given a class.
   */
  def apply[T <: Actor: ClassTag](): Props = createProps(implicitly[ClassTag[T]].runtimeClass.asSubclass(classOf[Actor]), List.empty)

  /**
   * Java API: create a Props given a class and its constructor arguments.
   */
  @varargs
  def create(clazz: Class[_ <: Actor], args: AnyRef*): Props = createProps(clazz, args.toList)

  /**
   * Internal API to create the Props
   */
  private def createProps(clazz: Class[_ <: Actor], args: Seq[Any]) = Props(classOf[InjectedProps.Producer], injector, clazz, args.toList)

}

object InjectedProps {

  /**
   * Actor producer through Guice injector.
   */
  class Producer private[guice] (injector: Injector, actorType: Class[_ <: Actor], args: Seq[Any]) extends IndirectActorProducer {

    def actorClass: Class[_ <: Actor] = actorType

    def produce(): Actor = {
      if (isActorSingleton) throwException("The actor class cannot be bound in a singleton scope.")
      if (args.isEmpty) {
        injector.getInstance(actorType)
      } else {
        argInjector.getInstance(actorType)
      }
    }

    private val isActorSingleton = {
      val key = Key.get(actorType)
      val binding = injector.getExistingBinding(key)
      scopeOf(binding) == Scopes.SINGLETON || hasSingletonAnnotation
    }

    private def scopeOf(binding: Binding[_]): Scope = {
      def visitor = new DefaultBindingScopingVisitor[Scope] {
        override def visitScope(scope: Scope): Scope = scope
      }
      if (binding != null) binding.acceptScopingVisitor(visitor) else Scopes.NO_SCOPE
    }

    private def hasSingletonAnnotation =
      actorType.isAnnotationPresent(classOf[javax.inject.Singleton]) ||
      actorType.isAnnotationPresent(classOf[com.google.inject.Singleton])

    private def throwException(messages: String*) = {
      import scala.collection.JavaConversions._
      throw new CreationException(messages.map(new Message(_)))
    }

    /**
     * @return a child injector with the passed parameter 
     */
    private def argInjector = injector.createChildInjector(new AbstractModule {
      def configure() = {
        argsToBind.foreach ( binding => {
          bind(binding.key).toInstance(binding.arg)
        })
      }
    })

    /**
     * Find the constructor with the `@Inject` annotation and
     * map argument value to each constructor parameter type
     * that is not already bound.
     * @return the binding args (key and instance).
     */
    private def argsToBind = {
      val constructor = actorType.getConstructors.find(injectAnnotation)
      if (constructor.isEmpty) throwException("Impossible to find a constructor annotated with @Inject annotation.") // TODO Create a test for this scenario
      val keys = constructor.map(asKeys).get
      val bindings = keys.filter {
        case key => injector.getExistingBinding(key) == null
      }.zip(args).map(toBindingArg)
      if (bindings.size != args.size) throwException(s"Impossible to find a constructor with ${args.size} not injected parameter(s)")
      bindings
    }

    private def injectAnnotation(constructor: Constructor[_]) =
      constructor.isAnnotationPresent(classOf[javax.inject.Inject]) ||
      constructor.isAnnotationPresent(classOf[com.google.inject.Inject])

    private def toBindingArg(keyArg: (Key[_], Any)) = new BindingArg(keyArg._1.asInstanceOf[Key[Any]], keyArg._2)

    private class BindingArg(val key: Key[Any], val arg: Any)

    private def asKeys(constructor: Constructor[_]) = {
      val types = constructor.getGenericParameterTypes
      val annotations = constructor.getParameterAnnotations
        .map( annotations => annotations.find(qualifierAnnotation) )
      types.zip(annotations).map {
         case (clazz, annotation) => if (annotation.isDefined) Key.get(clazz, annotation.get) else Key.get(clazz)
      }
    }

    private def qualifierAnnotation(annotation: Annotation) =
      annotation.annotationType.isAnnotationPresent(classOf[Qualifier]) ||
      annotation.annotationType.isAnnotationPresent(classOf[BindingAnnotation])

  }

}
