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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Simple annotation processor to create the services file for methods declaring to be static factory methods.
 * <p>Compilation errors are created for following cases:
 * <ul>
 * <li>annotated method is defined in an abstract class</li>
 * <li>annotated method is inside an inner class which is not public</li>
 * <li>annotated method is not public</li>
 * <li>annotated method is not static</li>
 * <li>annotated method returns void</li>
 * <li>given predicate is an inner class which is not static</li>
 * <li>given predicate is an abstract class</li>
 * <li>given predicate is of type interface</li>
 * <li>given predicate has no public non-argument constructor</li>
 * <li>the services cannot be written to META-INF/services for any reason</li>
 * </ul>
 * A warning is created if return type declared in annotation is not related to the return type of the annotated method.
 * </p>
 *
 * @author cperv
 * @version 0.2
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes(value = {"de.cp.staticfactories.method.StaticFactoryMethod", "de.cp.staticfactories.method.StaticFactoryMethods"})
public class StaticFactoryProcessor extends AbstractProcessor {

  private final Collection<String> enclosingClasses = new HashSet<>();

  @Override
  public final boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
    if (roundEnv.errorRaised()) {
      return false;
    }
    if (roundEnv.processingOver()) {
      writeServices();
      enclosingClasses.clear();
      return true;
    } else {
      return handleProcess(roundEnv);
    }
  }

  private boolean handleProcess(final RoundEnvironment roundEnv) {
    boolean ret = false;
    // get the methods annotated with a single annotation
    final Collection<Element> coll = new HashSet<>(roundEnv.getElementsAnnotatedWith(StaticFactoryMethod.class));
    // and add those using repeatable annotations
    coll.addAll(roundEnv.getElementsAnnotatedWith(StaticFactoryMethods.class));

    // now iterate on the annotated elements
    for (final Element givenMethod : coll) {
      final ExecutableElement method = (ExecutableElement) givenMethod;
      final StaticFactoryMethod[] annotationsByType = method.getAnnotationsByType(StaticFactoryMethod.class);

      // this should never happen - but you know how it is: it shouldn't, but it will under some strange, unexpected circumstances
      if (annotationsByType == null || annotationsByType.length == 0) {
        continue;
      }

      final TypeElement enclosingClazz = (TypeElement) method.getEnclosingElement();

      // add the element's class only if there are no error's in the definition
      // Note: do not check whether the class is already in the list to avoid adding, as then the compiler checks will not be performed
      if (checkMethodSignature(method) && checkEnclosingClass(enclosingClazz, method) && checkAnnotationValues(method)) {
        enclosingClasses.add(processingEnv.getElementUtils().getBinaryName(enclosingClazz).toString());
        ret = true;
      }
    }
    return ret;
  }

  /**
   * Checks the signature of the defining method and creates errors if the method is either not public or not static or returns void.
   *
   * @param element the method to check
   */
  private boolean checkMethodSignature(final ExecutableElement method) {
    boolean ret = true;
    final Messager messager = processingEnv.getMessager();

    if (!method.getModifiers().contains(Modifier.PUBLIC)) {
      messager.printMessage(Kind.ERROR, String.format("Method using annotation StaticFactoryMethod must be public: %s", method.getSimpleName().toString()), method);
      ret = false;
    }

    if (!method.getModifiers().contains(Modifier.STATIC)) {
      messager.printMessage(Kind.ERROR, String.format("Method using annotation StaticFactoryMethod must be static: %s", method.getSimpleName().toString()), method);
      ret = false;
    }

    // check return values
    final TypeMirror methodsReturnType = method.getReturnType();

    if (TypeKind.VOID == methodsReturnType.getKind()) {
      messager.printMessage(Kind.ERROR, "Method using annotation StaticFactoryMethod must have a return value.", method);
      ret = false;
    }

    return ret;

  }

  /**
   * Checks the class enclosing the method is not abstract.
   *
   * @param enclosingClazz the class encloding the method
   * @param method the method to check
   * @return {@code true} if no errors had been created
   */
  private boolean checkEnclosingClass(final TypeElement enclosingClazz, final ExecutableElement method) {
    boolean ret = true;
    final Messager messager = processingEnv.getMessager();

    if (enclosingClazz.getModifiers().contains(Modifier.ABSTRACT)) {
      // yes this might happen, if the annotated method just returns "null". In that case the compiler will not create an error
      messager.printMessage(Kind.ERROR, String.format("Method '%s' annotated with StaticFactoryMethod must not be in an abstract class.", method.getSimpleName().toString()),
          enclosingClazz);
      ret = false;
    }

    if (isEnclosedByKind(enclosingClazz, ElementKind.CLASS) && !enclosingClazz.getModifiers().contains(Modifier.PUBLIC)) {
      // factory method is inside an inner class
      messager.printMessage(Kind.ERROR, String.format("Method '%s' annotated with StaticFactoryMethod must be in a public class.", method.getSimpleName().toString()),
          enclosingClazz);
      ret = false;
    }

    return ret;
  }

  /**
   * Checks the values given in the annotation for possible issues. This includes unreachable or not-instantiatable predicate classes, but also unrelated return values.
   *
   * @param method the annotated method
   * @return {@code true} in case no issue were found
   */
  private boolean checkAnnotationValues(final ExecutableElement method) {
    boolean ret = true;

    final TypeMirror methodsReturnType = method.getReturnType();

    // obtain the mirrors reflecting the annotation
    final Collection<AnnotationMirror> annotationMirrors = getAnnotationMirrors(method);
    return annotationMirrors.stream()
        .map((AnnotationMirror annotationMirror) -> processAnnotationMirror(annotationMirror, methodsReturnType, method))
        .reduce(ret, (Boolean accumulator, Boolean item) -> accumulator & item);
  }

  private boolean processAnnotationMirror(final AnnotationMirror annotationMirror, final TypeMirror methodsReturnType, final ExecutableElement method) {
    boolean ret = true;
    final Messager messager = processingEnv.getMessager();
    for (final Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
      final String keyName = entry.getKey().getSimpleName().toString();
      final TypeElement valueAsElement = processingEnv.getElementUtils().getTypeElement(entry.getValue().getValue().toString());
      if ("returns".equals(keyName)) {
        // the return value was defined - check whether it corresponds to the method's return value
        // in the sense, that it must be a sub type of the same type of the method's return type
        final TypeMirror asType = valueAsElement.asType();
        if (!processingEnv.getTypeUtils().isSubtype(asType, methodsReturnType)) {
          messager.printMessage(Kind.MANDATORY_WARNING, String.format("Annotation defines that method returns an object of type %s, but this is neither the same, nor  a subtype "
                  + "of the method's return value (%s). This can cause problems when resolving the annotated method.",
              asType.toString(), methodsReturnType.toString()),
              method);
          ret = false;
        }
      }

      if ("predicate".equals(keyName)) {
        // a predicate is defined, let's perform some checks here
        ret &= checkPredicate(method, valueAsElement);
      }
    }
    return ret;
  }

  /**
   * Performs the checks of the predicate given in the annotation.
   *
   * @param method the annotated method - used to give a hint which method has the wrong predicate definition, if required
   * @param predicatesElement the predicate type element itself
   * @return {@code true} the predicate is valid (see clas documentation)
   */
  private boolean checkPredicate(final ExecutableElement method, final TypeElement predicatesElement) {
    boolean ret = true;
    final Messager messager = processingEnv.getMessager();

    final Collection<Modifier> predicatesModifiers = predicatesElement.getModifiers();
    if (isEnclosedByKind(predicatesElement, ElementKind.CLASS) && !predicatesModifiers.contains(Modifier.STATIC)) {
      // predicate is an inner class - in this case we need a static class
      messager.printMessage(Kind.ERROR, String.format("Predicate (%s) defined on method '%s' is an inner class and thus must be static.", predicatesElement.getSimpleName(),
          method.getSimpleName().toString()), method);
      ret = false;
    }

    if (predicatesModifiers.contains(Modifier.ABSTRACT)) {
      messager.printMessage(Kind.ERROR, String.format("Predicate (%s) defined on method '%s' must not be an abstract class.", predicatesElement.getSimpleName(),
          method.getSimpleName().toString()), method);
      ret = false;
    }

    if (ElementKind.INTERFACE == predicatesElement.getKind()) {
      messager.printMessage(Kind.ERROR, String.format("Predicate (%s) defined on method '%s' must not be an interface.", predicatesElement.getSimpleName(),
          method.getSimpleName().toString()), method);
      ret = false;
    }

    // check for the constructors of the predicate class
    // we need at least one, that is public and non-argumented
    final boolean noDefaultConstructor = predicatesElement.getEnclosedElements().stream()
        .filter(elem -> ElementKind.CONSTRUCTOR == elem.getKind())
        .noneMatch(elem -> ((ExecutableElement) elem).getParameters().isEmpty());

    if (noDefaultConstructor) {
      messager.printMessage(Kind.ERROR, String.format("Predicate (%s) defined on method '%s' must have a public no-argument constructor.", predicatesElement.getSimpleName(),
          method.getSimpleName().toString()), method);
      ret = false;
    }
    return ret;
  }

  /**
   * Gets the mirrors for the annotations on the given method. This method also resolves the annotations declared inside the container {@link StaticFactoryMethods}.
   *
   * @param method the method to get the annotation mirros for
   * @return a collection of mirrors of the annotations declared on the method
   */
  private Collection<AnnotationMirror> getAnnotationMirrors(final Element method) {
    final String singleAnnotationName = StaticFactoryMethod.class.getName();
    final String containerAnnotationName = StaticFactoryMethods.class.getName();

    final Collection<AnnotationMirror> singleMirrors = new HashSet<>();

    for (final AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
      final Name binaryName = processingEnv.getElementUtils().getBinaryName((TypeElement) annotationMirror.getAnnotationType().asElement());
      if (binaryName.contentEquals(containerAnnotationName)) {
        // the method has the annotation multiple times
        // so let's get the included ones
        final AnnotationMirrorResolveVisitor avVisit = new AnnotationMirrorResolveVisitor();
        // go and visit the annotations inside the container
        annotationMirror.getElementValues().values().forEach(value -> value.accept(avVisit, singleMirrors));
      } else if (binaryName.contentEquals(singleAnnotationName)) {
        singleMirrors.add(annotationMirror);
      }
    }

    return singleMirrors;
  }

  /**
   * Nullsafe check to evaluate whether the enclosing element of the passed element is of the given type.
   *
   * @param element the element to check
   * @param kind the type to check against
   * @return {@code true} in case the {@code element} is enclosed by and element of the given type
   */
  private boolean isEnclosedByKind(final TypeElement element, final ElementKind kind) {
    return element.getEnclosingElement() != null && element.getEnclosingElement().getKind() == kind;
  }

  /**
   * Finally we are done - let the processor write the services to the META-INF directory.
   */
  private void writeServices() {
    try {
      final Filer filer = processingEnv.getFiler();
      final FileObject out = filer.createResource(StandardLocation.CLASS_OUTPUT, "", StaticFactoryUtil.SERVICES_FILE, new Element[0]);

      try (final OutputStream outputStream = out.openOutputStream(); final PrintWriter pw = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
        enclosingClasses.forEach(pw::println);

        pw.flush();
      }

    } catch (final IOException ex) {
      processingEnv.getMessager().printMessage(Kind.ERROR, "Cannot write service file with static factory enclosing classes.");
    }
  }
}
