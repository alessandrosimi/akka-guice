package com.project.akka.guice

import akka.actor._
import javax.inject.{Qualifier, Inject}
import com.google.inject._
import scala.annotation.varargs
import java.lang.reflect.Constructor
import java.lang.annotation.Annotation

@Singleton
class InjectedProps @Inject() (injector: Injector) {

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

    def produce(): Actor = { // TODO Create a precondition over the class, it should not be bound as singleton
      if (args.isEmpty) {
        injector.getInstance(actorType)
      } else {
        argInjector.getInstance(actorType)
      }
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
      require(constructor.isDefined, "Impossible to find a constructor annotated with @Inject annotation.") // TODO Use the right exception
      val keys = constructor.map(asKeys).get
      //require(keys.toSet.size == keys.size && keys.size != 1) TODO Decide to add the validation here or not
      keys.filter {
        case key => injector.getExistingBinding(key) == null // TODO Filter with JIT binding if there are no performance problem
      }.zip(args).map(toBindingArg)
    }

    private def injectAnnotation(constructor: Constructor[_]) = // TODO Create a test for both Inject annotation
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

    private def qualifierAnnotation(annotation: Annotation) = // TODO Create a test for both qualifiers
      annotation.annotationType.isAnnotationPresent(classOf[Qualifier]) ||
      annotation.annotationType.isAnnotationPresent(classOf[BindingAnnotation])

  }

}
