# dynamic-static-factory-java
Small predicates based framework allowing the usage of static factory methods without the need to hard bind their classes and method names.

## Motivation
Static factory methods are a commonly used approach to create objects in java.

Objects created this way are normally used to perform a particular job. Some developers are naming this a service.

Here is a little example:
```
/**
 * Service that works only if the processed String does not start with an 'a'.
 */
public class Service1 {
  
  /**
   * Creates a new object of type Service1, taking the given input string.
   * @throws IllegalArgumentException if first character is an 'a'
   */
  public static Service1 get(final String input) {
    if(input.startsWith("a")) {
      throw new IllegalArgumentException();
    }
    // create our service
  }
}

/**
 * Service that works only if the processed String does not start with a 'b'.
 */
public class Service2 {
  
  /**
   * Creates a new object of type Service1, taking the given input string.
   * @throws IllegalArgumentException if the first character is a 'b'
   */
  public static Service2 get(final String input) {
    if(input.startsWith("b")) {
      throw new IllegalArgumentException();
    }
   // create our service
  }
}
```
This is a quite constructed example, but it demonstrate the issue very well.

What would a developer do now, to decide which service to use?
Obviously there are different options:
1. iterate on all services, call the create method and catch the exception
   - workflow by exception? Really? That's nasty!
   - what if the services are not known at runtime?
   - what if there are tons of services? That might result in long if-else clauses or in collections listing all the services and these collections must be maintained...
2. create an interface/super class for all the services, which defines such a service must have a method, we can use to check whether it is usable, or not
   - still: what if the services are not known at runtime?
   - still: what if there are tons of services?
3. Creating a utility/helper class
4. May some other nasty things :)

And now think about the following:
- other code locations must also decide which service to be used
- the input for the static factory methods is slightly different

If you are in such (or similar) situation you might consider using this little framework.

It requires a little change of you code, but frees you from thinking about all the stuff above and btw. provides your code with loose coupling.
So here's the example using the framework:
```
/**
 * Service that works only if the processed String does not start with an 'a'.
 */
public class Service1 {
  
  /**
   * Creates a new object of type Service1, taking the given input string.
   */
  @StaticFactoryMethod(predicate = Decider.class)
  public static Service1 get(final String input) {
    // create our service
  }

  public static final Decider implements Predicate<String> {
    public boolean test(String t) {
      return !input.startsWith("a")
    }
  }
}

/**
 * Service that works only if the processed String does not start with a 'b'.
 */
public class Service2 {
  
  /**
   * Creates a new object of type Service1, taking the given input string.
   */
  @StaticFactoryMethod(predicate = Decider.class)
  public static Service2 get(final String input) {
   // create our service
  }
  
  public static final Decider implements Predicate<String> {
    public boolean test(String t) {
      return !input.startsWith("b")
    }
  }
}
```

As you can see, the classes have an inner class now (could also be an external one, it really does not matter) which is referenced from an annotation on our static factory method.

Consuming code can now just call a single line:
`Object servicetoUse = StaticFactoryUtitl.getObject(null, aString, aString)`
And that's it already.
