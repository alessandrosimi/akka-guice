Akka Guice
====

**Latest release:** Pre Release<br/>
**License:** [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0)

AkkaGuice delegates the creation of the [Akka](http://akka.io) actors to [Guice](https://github.com/google/guice).

AkkaGuice is very simple to use. Import the project and use the `InjectedProps` class provided by Guice to create the actor instead of `Props`.
```Java
@Inject private InjectedProps props;
...
ActorRef ref = system.actorOf(props.create(MyActor.class));
```
`InjectedProps` has the same API of the `Props` Akka version, availabile for Scala and Java.
```Scala
val ref = system.actorOf(props(classOf[MyActor]))
```
The actor is create by Guice so it benefits of the injection (constructor, methods and properites) and the AOP.
```Java
public class MyActor {
  private final MyService myService;
  @Inject
  public class MyActor(MyService myService) {
    this.myService = myService;
  }
  ...
}
```
The actor can be built with **not-injected** parameters.
```Java
ActorRef ref = system.actorOf(props.create(MyActor.class, "Param"));
...
public class MyActor {
  private final MyService myService;
  @Inject
  public class MyActor(MyService myService, String param) {
    this.myService = myService;
  }
  ...
}
```
They must be different Guice types (or Guice binding key).
For example if the actor needs 2 `String`s parameters, one of them should be annotated with `@Named("...")` or another qualifier annotation.
```Java
public class MyActor {
  ...
  @Inject
  public class MyActor(MyService myService, String param, @Named("other") param2) {
    this.myService = myService;
  }
  ...
}
```
AkkaGuice does also a sanity check to avoid the `Singleton` binding of actor.
