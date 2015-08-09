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

@Singleton
class InjectedProps @Inject() (injector: Injector) { // TODO Write documentation

  /**
   * Scala API: create a Props given a class and its constructor arguments.
   */
  def apply(clazz: Class[_ <: Actor], args: Any*): Props = Props(classOf[InjectedProps.Producer], injector, clazz, args.toList)

  /**
   * Java API: create a Props given a class and its constructor arguments.
   */
  @varargs
  def create(clazz: Class[_ <: Actor], args: AnyRef*): Props = Props(classOf[InjectedProps.Producer], injector, clazz, args.toList)

}

object InjectedProps {
  class Producer private[guice] (injector: Injector, actorType: Class[_ <: Actor], args: Seq[Any]) extends IndirectActorProducer {

    def actorClass: Class[_ <: Actor] = actorType

    def produce(): Actor = {
      if (isSingletonActor) throwException("The actor class cannot be bound in a singleton scope.")
      if (args.isEmpty) {
        injector.getInstance(actorType)
      } else {
        argInjector.getInstance(actorType)
      }
    }

    private def isSingletonActor = {
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

    private val hasSingletonAnnotation =
      actorType.isAnnotationPresent(classOf[javax.inject.Singleton]) ||
      actorType.isAnnotationPresent(classOf[com.google.inject.Singleton])

    private def throwException(messages: String*) = {
      import scala.collection.JavaConversions._
      throw new CreationException(messages.map(new Message(_)))
    }

    private def argInjector = injector.createChildInjector(new AbstractModule {
      def configure() = {
        bindingArgs.foreach ( binding => {
          bind(binding.key).toInstance(binding.arg)
        })
      }
    })

    /**
     * Find the constructor with the `@Inject` annotation
     * @return the binding args (key and instance).
     */
    private def bindingArgs = {
      val constructor = actorType.getConstructors.find(injectAnnotation)
      if (constructor.isEmpty) throwException("Impossible to find a constructor annotated with @Inject annotation.") // TODO Create a test for this scenario
      val keys = constructor.map(asKeys).get
      //require(keys.toSet.size == keys.size && keys.size != 1) TODO Decide to add the validation here or not
      keys.filter {
        case key => injector.getExistingBinding(key) == null // TODO Filter with JIT binding if there are no performance problem
      }.zip(args).map(toBindingArg)
    }

    private def injectAnnotation(constructor: Constructor[_]) =
      constructor.isAnnotationPresent(classOf[javax.inject.Inject]) ||
      constructor.isAnnotationPresent(classOf[com.google.inject.Inject])

    private def toBindingArg(keyArg: (Key[_], Any)) = new BindingArg(keyArg._1.asInstanceOf[Key[Any]], keyArg._2)

    private class BindingArg(val key: Key[Any], val arg: Any)

    private def asKeys(constructor: Constructor[_]) = {
      val types = constructor.getGenericParameterTypes // TODO Create a test for generics
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
