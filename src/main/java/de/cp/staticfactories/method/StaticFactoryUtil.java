/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package de.cp.staticfactories.method;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities to load and invoke known (registered) static factory methods.
 * <p>
 * A static factory method is considered as being known, when its enclosing class has an entry in the file
 * {@code <build-output-directory>\classes\META-INF\services\de.cp.staticfactories.method.StaticFactoryMethod}.<br>
 * To register a factory method, add the annotation {@link StaticFactoryMethod} on it, fill the required values and make sure there are no compilation errors.<br> After a build the enclosing class of
 * the factory method will appear in the mentioned file.
 * </p>
 *
 * @author cperv
 * @version 0.2
 */
public final class StaticFactoryUtil {

  /**
   * The META-INF filename for the Service Lookup.
   */
  public static final String SERVICES_FILE = "META-INF/services/de.cp.staticfactories.method.StaticFactoryMethod";

  private static final Logger LOG = LogManager.getLogger(StaticFactoryUtil.class);

  private static final Collection<MethodMetaData> FACTORY_METHODS = new HashSet<>();

  private static final Predicate<Method> PREDICATE_METHOD_SELECTOR = new IsTestMethodPredicate().and(new IsNotSyntheticTestMethod());

  private StaticFactoryUtil() {
  }

  /**
   * Invokes a static factory method that fits the given parameters and returns the object created by it.
   * <p>
   * Static factory methods are resolved in the following way:
   * <ul>
   * <li>Retrieve all known (registered - see class documentation) factory methods</li>
   * <li>If a specific class is requested (that is {@code requestedClass != null}) all method's are checked whether they return this class directly or declares in the annotation to return it.</li>
   * <li>For all methods left check whether their predicate defined in the annotation (if defined) returns {@code true} for the given predicate input.</li>
   * <li>Find a method that can take the values given in {@code factoryInput} and invoke it.</li>
   * </ul>
   * </p>
   * <p>
   * <b>Note on return class resolving</b>
   * If using the {@code requestedClass} with a non-null value, this method will lookup the return type of a factory method and if present the return type defined in the annotation.<br> Only if there
   * is an exact match with either one of them, the factory is considered to be usable.<br> There's no check whether the passed class is a super or sub type of the class defined in the method's
   * signature or the annotation.
   * </p>
   *
   * @param <T> the type of the class of the object to obtain
   * @param <I> the type of the input of the predicate
   * @param requestedClass the class type that should be returned. Can be ommitted.
   * @param predicateInput the input for the predicate inside the @StaticFactoryMethod annotation. Can be omitted.
   * @param factoryInput the input to give to a found factory method
   * @return the object created by the found factory method. Might be {@code null} in case no factory was found or the factory returned null by itself
   */
  public static <T, I> T getObject(final Class<T> requestedClass, final I predicateInput, final Object... factoryInput) {
    T ret = null;

    // load the factories - we might want to cache them?
    reload();

    Stream<MethodMetaData> methods = FACTORY_METHODS.stream().parallel();

    if (requestedClass != null) {
      // a specific class is requested
      // so either the annotation must declare it or the method's return type must be of it
      methods = methods.filter((MethodMetaData m) -> m.annotatedReturnType == requestedClass || m.methodReturnType == requestedClass);
    }

    // test whether we have a predicate defined and if so, check it is can be used and if so whether it delivers true
    methods = methods.filter((MethodMetaData m) -> m.checkPredicate != null && canUsePredicate(m.checkPredicate, predicateInput) && m.checkPredicate.test(predicateInput));

    final Collection<MethodMetaData> allLeft = methods.collect(Collectors.toSet());
    if (allLeft.size() != 1) {
      LOG.info("Fitting methods found: " + allLeft.size() + ", expected is 1.");
    }

    // if we have multiple left, just iterate on them, until one creates our object
    for (final Iterator<MethodMetaData> iterator = allLeft.iterator(); iterator.hasNext() && ret == null;) {
      final MethodMetaData next = iterator.next();
      ret = createObject(next, factoryInput);
    }

    return ret;
  }

  /**
   * Checks whether the given predicate's apply method can work with the given predicate input.
   *
   * @param <I> the type of the data to be passed to the apply method
   * @param predicate the predicate to check
   * @param predicateInput the input data to be passed to the predicate
   * @return {@code true} if the apply method's input parameter is class equal to the given input class
   */
  private static <I> boolean canUsePredicate(final Predicate<?> predicate, final I predicateInput) {
    /*
     * due to inheritance we will have at least two "test" methods - one with Object as parameter type and one with the specified type
     * the method taking the object is "synthetic", so do not take care for it
     * and if the parameter of the test method is not a superclass of superinterface of the desired input, skip it too
     */
    final Optional<Method> findFirst = Arrays.stream(predicate.getClass().getMethods())
            .filter(PREDICATE_METHOD_SELECTOR.and((Method m) -> m.getParameterTypes()[0].isAssignableFrom(predicateInput.getClass())))
            .findFirst();

    return findFirst.isPresent();
  }

  @SuppressWarnings("unchecked")
  private static <T> T createObject(final MethodMetaData data, final Object... factoryInput) {
    T ret = null;
    if (data != null) {
      try {
        // we do it the easy, but nasty way (control flow by exception)
        ret = (T) data.method.invoke(null, factoryInput);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        if (LOG.isErrorEnabled()) {
          LOG.error(String.format("Object could not be created using the method: %s. With the parameters: %s", data.method.toString(), Arrays.toString(factoryInput)), ex);
        }
      }
    }
    return ret;
  }

  /**
   * Reloads the classes providing static factory methods. Public to enable pre-fetching later.
   */
  public static void reload() {
    FACTORY_METHODS.clear();
    final URL resource = ClassLoader.getSystemResource(SERVICES_FILE);

    if (resource == null) {
      LOG.info("No services for static factories found.");
    } else {
      load(resource);
    }
  }

  private static void load(final URL url) {
    final Properties properties = new Properties();
    try (final InputStream is = url.openStream()) {
      properties.load(is);
    } catch (final IOException ex) {
      if (LOG.isErrorEnabled()) {
        LOG.error("Problems opening a stream for " + url, ex);
      }
    }

    loadClasses(properties);

  }

  private static void loadClasses(final Properties properties) {
    for (final Object object : properties.keySet()) {
      try {
        final Class<?> clazz = Class.forName((String) object);

        Arrays.stream(clazz.getDeclaredMethods()).filter((Method method) -> Modifier.isPublic(method.getModifiers())
                && Modifier.isStatic(method.getModifiers())
                && method.getAnnotation(StaticFactoryMethod.class) != null)
                .forEach(StaticFactoryUtil::resolveAnnotation);

      } catch (final ClassNotFoundException ex) {
        if (LOG.isInfoEnabled()) {
          LOG.info("No class could be found for entry '" + object + "'", ex);
        }
      } catch (final SecurityException ex) {
        if (LOG.isInfoEnabled()) {
          LOG.info("Couldn't load methods for class: " + object + ". See exception details: ", ex);
        }
      }
    }
  }

  private static void resolveAnnotation(final Method method) {
    for (final StaticFactoryMethod annotation : method.getAnnotationsByType(StaticFactoryMethod.class)) {

      final MethodMetaData data = new MethodMetaData();
      data.method = method;
      data.methodReturnType = method.getReturnType();

      if (annotation.returns() != Class.class) {
        data.annotatedReturnType = annotation.returns();
      }

      data.checkPredicate = getPredicate(annotation);

      FACTORY_METHODS.add(data);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Predicate<T> getPredicate(final StaticFactoryMethod annotation) {
    Predicate<T> ret = null;
    if (annotation.predicate() != Predicate.class) {
      try {
        ret = annotation.predicate().newInstance();
      } catch (InstantiationException | IllegalAccessException ex) {
        LOG.info(() -> "Cannot create object for given predicate class: " + annotation.predicate(), ex);
      }
    }
    return ret;
  }

  private static final class MethodMetaData {

    private Method method;
    private Class<?> methodReturnType;
    private Class<?> annotatedReturnType;
    private Predicate<Object> checkPredicate;
  }
  
  private static final class IsTestMethodPredicate implements Predicate<Method> {
    @Override
    public boolean test(final Method method) {
      return "test".equals(method.getName());
    }
  }
  
  private static final class IsNotSyntheticTestMethod implements Predicate<Method> {
    @Override
    public boolean test(final Method method) {
      return !method.isSynthetic();
    }
  }
}
