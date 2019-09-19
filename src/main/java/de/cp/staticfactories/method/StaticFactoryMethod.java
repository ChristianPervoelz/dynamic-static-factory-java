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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;

/**
 * An annotation that can be used for static factory methods to create objects without actually knowing which class is creating a requested object.
 * <p>This annotation should be used to increase loose coupling and support the usage of the single responsibility pattern.</p>
 * <p>This annotation is repeatable so it can be used for example with different predicates for different purposes.</p>
 * <p>
 * <b>Important note:</b><br>
 * If your static factory method's return value is a super class of the class to be created it is absolutely necessary to use the {@code returns} value of this.<br> Example:
 * <pre>
 * {@code @StaticFactoryMethod()}
 *  public static Collection create(Object... objects) {
 *    return Lists.newArrayList(objects);
 *  }
 * </pre>
 * If we now call
 * <pre>
 * Collection coll = StaticFactoryUtil.getObject(ArrayList.class, whatEver, whatEverToo);
 * </pre>
 * {@code coll} will be {@code null}, as the framework will look for a method that is returning an {@coce ArrayList}, but our mehtod returns a collection only.
 * <br><br>
 * There are two solutions now. Either we change our call to:
 * <pre>
 * Collection coll = StaticFactoryUtil.getObject(Collection.class, whatEver, whatEverToo);
 * </pre>
 * or we change our annotation to
 * <pre>
 * {@code @StaticFactoryMethod(returns = ArrayList.class}}
 * </pre>
 * Both ways will work, whereas the latter one is a bit safer.
 * </p>
 *
 * @author cperv
 * @version 0.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(StaticFactoryMethods.class)
public @interface StaticFactoryMethod {

  /**
   * Field to indicate which class type is returned in reality by the method. This should be used if the method returns a super class of the object it is used to create for.
   * <p>Example:
   * <pre>
   * public static Collection create(Object... objects) {
   *   List ret = new ArrayList<>();
   *   // whatever is done with the passed objects
   *   return ret;
   * }
   * </pre>
   * In the example a list is returned, but the method's return type is a collection, so the annotation could declare it returns an ArrayList, like this:
   * <pre>
   * @StaticFactoryMethod(returns = ArrayList.class)
   * public static Collection create(Object... objects) {
   * </pre>
   * </p>
   *
   * @return the real class created by the annotated method
   */
  Class<?> returns() default java.lang.Object.class;

  /**
   * Predicate to be used to check whether the annotated method can be used to create an requested object. Please note, this predicate must be reachable by the compiler, what means it must be either a
   * public static inner class or a public non-static outer class with a default constructor in both cases.
   *
   * @return the predicate to check whether the annotated method can be used
   */
  Class<? extends Predicate> predicate() default Predicate.class;
}
