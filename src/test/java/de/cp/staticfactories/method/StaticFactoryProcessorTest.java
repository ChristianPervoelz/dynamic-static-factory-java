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


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 *
 * @version
 * @author cperv
 * @since
 */
public class StaticFactoryProcessorTest {

  @Mock
  private ProcessingEnvironment processingEnvironment;

  @Mock
  private RoundEnvironment roundEnvironment;

  @Mock
  private ExecutableElement annotatedMethod;

  @Mock
  private StaticFactoryMethod singleAnnotationMock;

  @Mock
  private TypeMirror methodReturnTypeMirror;

  /**
   * The type mirror of the annotation value used for the "returns" method of the annotation
   */
  @Mock
  private TypeMirror annotationValueReturnsTypeMirror;

  @Mock
  private TypeElement methodEnclosingClass;

  @Mock
  private AnnotationMirror singleAnnotationMirror;

  /**
   * The type element of the predicate.
   */
  @Mock
  private TypeElement predicateTypeElement;

  @Mock
  private Elements elementUtils;

  @Mock
  private Types typeUtils;

  private TestMessager messager;

  private AbstractProcessor testee;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    testee = new TestProcessor(processingEnvironment);

    messager = new TestMessager();
    Mockito.when(processingEnvironment.getMessager()).thenReturn(messager);

    Mockito.when(processingEnvironment.getElementUtils()).thenReturn(elementUtils);
    Mockito.when(processingEnvironment.getTypeUtils()).thenReturn(typeUtils);

    // as we do not want to write files, we'll never come to processingOver
    Mockito.when(roundEnvironment.processingOver()).thenReturn(Boolean.FALSE);
    Mockito.when(roundEnvironment.errorRaised()).thenReturn(Boolean.FALSE);

    // let's fully initialize the method
    final Set<Modifier> methodModifiers = newHashSet(Modifier.PUBLIC, Modifier.STATIC);
    Mockito.when(annotatedMethod.getModifiers()).thenReturn(methodModifiers);
    Mockito.when(methodReturnTypeMirror.getKind()).thenReturn(TypeKind.OTHER);
    Mockito.when(annotatedMethod.getReturnType()).thenReturn(methodReturnTypeMirror);

    final Name methodName = Mockito.mock(Name.class);
    Mockito.when(methodName.toString()).thenReturn("Test method");
    Mockito.when(annotatedMethod.getSimpleName()).thenReturn(methodName);

    Mockito.when(annotatedMethod.getEnclosingElement()).thenReturn(methodEnclosingClass);
    Mockito.when(methodEnclosingClass.getModifiers()).thenReturn(newHashSet(Modifier.PUBLIC));
    Mockito.when(methodEnclosingClass.getKind()).thenReturn(ElementKind.CLASS);

    final Name enclosingClassName = Mockito.mock(Name.class);
    Mockito.when(enclosingClassName.toString()).thenReturn("EnclosingClass");
    Mockito.when(elementUtils.getBinaryName(methodEnclosingClass)).thenReturn(enclosingClassName);

    // use a set without generics as else we get compiler problems
    final Set newHashSet = newHashSet(annotatedMethod);
    Mockito.when(roundEnvironment.getElementsAnnotatedWith(StaticFactoryMethod.class)).thenReturn(newHashSet);
    Mockito.when(annotatedMethod.getAnnotationsByType(StaticFactoryMethod.class)).thenReturn(new StaticFactoryMethod[]{singleAnnotationMock});

    // set the annotation mirror
    setupAnnotationMirror();
  }

  private void setupAnnotationMirror() {
    final DeclaredType declaredType = Mockito.mock(DeclaredType.class);
    final TypeElement samElem = Mockito.mock(TypeElement.class);
    final Name samName = Mockito.mock(Name.class);
    Mockito.when(declaredType.asElement()).thenReturn(samElem);
    Mockito.when(singleAnnotationMirror.getAnnotationType()).thenReturn(declaredType);
    Mockito.when(elementUtils.getBinaryName(samElem)).thenReturn(samName);
    Mockito.when(samName.contentEquals(StaticFactoryMethod.class.getName())).thenReturn(Boolean.TRUE);

    final List annoMirrors = new ArrayList<AnnotationMirror>();
    annoMirrors.add(singleAnnotationMirror);
    Mockito.when(annotatedMethod.getAnnotationMirrors()).thenReturn(annoMirrors);

    // build the values
    final Pair<ExecutableElement, AnnotationValue> returns = setupAnnoMirrorReturns();
    final Pair<ExecutableElement, AnnotationValue> predicate = setupAnnoMirrorPredicate();
    final Map newHashMap = new HashMap<>();
    newHashMap.put(returns.getLeft(), returns.getRight());
    newHashMap.put(predicate.getLeft(), predicate.getRight());
    Mockito.when(singleAnnotationMirror.getElementValues()).thenReturn(newHashMap);
  }

  private Pair<ExecutableElement, AnnotationValue> setupAnnoMirrorReturns() {
    final ExecutableElement returnElement = Mockito.mock(ExecutableElement.class);
    final Name returnElemName = Mockito.mock(Name.class);
    Mockito.when(returnElemName.toString()).thenReturn("returns");
    Mockito.when(returnElement.getSimpleName()).thenReturn(returnElemName);

    final AnnotationValue annoValue = Mockito.mock(AnnotationValue.class);
    final Object valueOfValue = new Object();
    Mockito.when(annoValue.getValue()).thenReturn(valueOfValue);

    final TypeElement annoValueTypeElem = Mockito.mock(TypeElement.class);
    Mockito.when(elementUtils.getTypeElement(valueOfValue.toString())).thenReturn(annoValueTypeElem);

    Mockito.when(annoValueTypeElem.asType()).thenReturn(annotationValueReturnsTypeMirror);

    Mockito.when(typeUtils.isSubtype(annotationValueReturnsTypeMirror, methodReturnTypeMirror)).thenReturn(Boolean.TRUE);

    return Pair.of(returnElement, annoValue);
  }

  private Pair<ExecutableElement, AnnotationValue> setupAnnoMirrorPredicate() {
    final ExecutableElement returnElement = Mockito.mock(ExecutableElement.class);
    final Name returnElemName = Mockito.mock(Name.class);
    Mockito.when(returnElemName.toString()).thenReturn("predicate");
    Mockito.when(returnElement.getSimpleName()).thenReturn(returnElemName);

    final AnnotationValue annoValue = Mockito.mock(AnnotationValue.class);
    final Object valueOfValue = new Object();
    Mockito.when(annoValue.getValue()).thenReturn(valueOfValue);

    Mockito.when(elementUtils.getTypeElement(valueOfValue.toString())).thenReturn(predicateTypeElement);

    Mockito.when(predicateTypeElement.asType()).thenReturn(annotationValueReturnsTypeMirror);
    Mockito.when(predicateTypeElement.getKind()).thenReturn(ElementKind.CLASS);
    Mockito.when(predicateTypeElement.getModifiers()).thenReturn(newHashSet(Modifier.PUBLIC));

    // create a default constructor
    final ExecutableElement constructorElement = Mockito.mock(ExecutableElement.class);
    Mockito.when(constructorElement.getKind()).thenReturn(ElementKind.CONSTRUCTOR);
    Mockito.when(constructorElement.getParameters()).thenReturn(new ArrayList<>());

    List constructorList = newArrayList(constructorElement);
    Mockito.when(predicateTypeElement.getEnclosedElements()).thenReturn(constructorList);

    return Pair.of(returnElement, annoValue);
  }

  @Test
  public void testProcessErrorRaised() {
    Mockito.when(roundEnvironment.errorRaised()).thenReturn(Boolean.TRUE);
    assertFalse(testee.process(null, roundEnvironment));
  }

  @Test
  public void testProcessEmptyAnnotations() {
    // no check for null required, as in reality this set will not be null
    Mockito.when(roundEnvironment.getElementsAnnotatedWith(StaticFactoryMethod.class)).thenReturn(newHashSet());
    assertFalse(testee.process(newHashSet(), roundEnvironment));
  }

  @Test
  public void testNoAnnotatedElements() {
    Mockito.when(roundEnvironment.getElementsAnnotatedWith(StaticFactoryMethod.class)).thenReturn(newHashSet());
    Mockito.when(roundEnvironment.getElementsAnnotatedWith(StaticFactoryMethods.class)).thenReturn(newHashSet());

    assertFalse(testee.process(newHashSet(), roundEnvironment));
  }

  @Test
  public void testMethodWithoutAnnotation() {
    // use a set without generics as else we get compiler problems
    Mockito.when(annotatedMethod.getAnnotationsByType(StaticFactoryMethod.class)).thenReturn(new StaticFactoryMethod[]{});

    assertFalse(testee.process(newHashSet(), roundEnvironment));
  }

  @Test
  public void testMethodSignatureIsCorrect() {
    // remove the annotation mirrors so we do not run into the predicate checkers
    Mockito.when(annotatedMethod.getAnnotationMirrors()).thenReturn(new ArrayList<>());

    assertTrue(testee.process(newHashSet(), roundEnvironment));
    assertTrue(messager.messages.isEmpty());
  }

  @Test
  public void testMethodNotPublic() {

    Mockito.when(annotatedMethod.getModifiers()).thenReturn(newHashSet(Modifier.PRIVATE, Modifier.STATIC));

    assertFalse(testee.process(newHashSet(), roundEnvironment));
    assertThat(messager.messages.size(), is(1));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertEquals(annotatedMethod, message.element);
  }

  @Test
  public void testMethodNotStatic() {
    Mockito.when(annotatedMethod.getModifiers()).thenReturn(newHashSet(Modifier.PUBLIC));

    assertFalse(testee.process(newHashSet(), roundEnvironment));
    assertThat(messager.messages.size(), is(1));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertEquals(annotatedMethod, message.element);
  }

  @Test
  public void testMethodReturnsVoid() {
    Mockito.when(methodReturnTypeMirror.getKind()).thenReturn(TypeKind.VOID);

    assertFalse(testee.process(newHashSet(), roundEnvironment));
    assertThat(messager.messages.size(), is(1));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertEquals(annotatedMethod, message.element);
  }

  @Test
  public void testEnclosingClassIsCorrect() {
    // remove the annotation mirrors so we do not run into the predicate checkers
    Mockito.when(annotatedMethod.getAnnotationMirrors()).thenReturn(new ArrayList<>());

    assertTrue(testee.process(newHashSet(), roundEnvironment));
    assertTrue(messager.messages.isEmpty());

    // check inner class
    Mockito.when(methodEnclosingClass.getEnclosingElement()).thenReturn(methodEnclosingClass);
    assertTrue(testee.process(newHashSet(), roundEnvironment));
    assertTrue(messager.messages.isEmpty());
  }

  @Test
  public void testEnclosingClassIsAbstract() {
    Mockito.when(methodEnclosingClass.getModifiers()).thenReturn(newHashSet(Modifier.ABSTRACT));
    assertFalse(testee.process(newHashSet(), roundEnvironment));
    assertThat(messager.messages.size(), is(1));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertTrue(StringUtils.contains(message.message, annotatedMethod.getSimpleName().toString()));
    assertEquals(methodEnclosingClass, message.element);
  }

  @Test
  public void testEnlcosingClassIsInnerAndNotPublic() {
    // re-use our class - it's a mock
    Mockito.when(methodEnclosingClass.getEnclosingElement()).thenReturn(methodEnclosingClass);
    Mockito.when(methodEnclosingClass.getModifiers()).thenReturn(newHashSet(Modifier.PROTECTED));

    assertFalse(testee.process(newHashSet(), roundEnvironment));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertTrue(StringUtils.contains(message.message, annotatedMethod.getSimpleName().toString()));
    assertEquals(methodEnclosingClass, message.element);
  }

  /**
   * Checks a warning is written in case the method's return type is not related to the one declared in the annotation.
   */
  @Test
  public void testDeclaredReturnDoesNotFitAnnotationReturn() {
    // we haven't setup the type mirror for the annotations "returns" method properly, so we use null here
    Mockito.when(typeUtils.isSubtype(annotationValueReturnsTypeMirror, methodReturnTypeMirror)).thenReturn(Boolean.FALSE);

    assertFalse(testee.process(newHashSet(), roundEnvironment));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.MANDATORY_WARNING));
    assertTrue(message.message.length() > 0);
    assertEquals(annotatedMethod, message.element);
  }

  @Test
  public void testPredicateIsOk() {
    assertTrue(testee.process(newHashSet(), roundEnvironment));
    assertTrue(messager.messages.isEmpty());
  }

  @Test
  public void testPredicateIsInterface() {
    Mockito.when(predicateTypeElement.getKind()).thenReturn(ElementKind.INTERFACE);

    assertFalse(testee.process(newHashSet(), roundEnvironment));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertTrue(StringUtils.contains(message.message, "interface"));
    assertEquals(annotatedMethod, message.element);
  }

  @Test
  public void testPredicateIsAbstractClass() {
    Mockito.when(predicateTypeElement.getModifiers()).thenReturn(newHashSet(Modifier.ABSTRACT));
    assertFalse(testee.process(newHashSet(), roundEnvironment));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertTrue(StringUtils.contains(message.message, "abstract"));
    assertEquals(annotatedMethod, message.element);
  }

  /**
   * Tests whether there is a proper error message in the messager of the processor in case the predicate given by the annotation has a non-default constructor (means not public or has arguments).
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testPredicateHasNoDefaultConstructor() {
    List<? extends Element> enclosedElements = predicateTypeElement.getEnclosedElements();
    final List newArrayList = newArrayList((VariableElement)null);
    Mockito.when(((ExecutableElement)enclosedElements.iterator().next()).getParameters()).thenReturn(newArrayList);

    assertFalse(testee.process(newHashSet(), roundEnvironment));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertTrue(StringUtils.contains(message.message, "no-argument"));
    assertEquals(annotatedMethod, message.element);
  }

  @Test
  public void testPredicateEnclosedInInnerNotStaticClass() {
    Mockito.when(predicateTypeElement.getEnclosingElement()).thenReturn(methodEnclosingClass);
    Mockito.when(methodEnclosingClass.getKind()).thenReturn(ElementKind.CLASS);

    assertFalse(testee.process(newHashSet(), roundEnvironment));

    TestMessager.MessageElements message = messager.messages.iterator().next();
    assertThat(message.kind, is(Diagnostic.Kind.ERROR));
    assertTrue(message.message.length() > 0);
    assertTrue(StringUtils.contains(message.message, "inner"));
    assertTrue(StringUtils.contains(message.message, "static"));
    assertEquals(annotatedMethod, message.element);
  }
  
  private <X> Set<X> newHashSet(X... elements) {
    Set<X> ret = new HashSet<>(elements.length);
    Collections.addAll(ret, elements);
    return ret;
  }
  
  private <X> List<X> newArrayList(X... elements) {
    List<X> ret = new ArrayList<>();
    Collections.addAll(ret, elements);
    return ret;
  }

  /**
   * Subclass of StaticFactoryProcessor to get access to the field processingEnv of AbstractProcessor
   */
  private static final class TestProcessor extends StaticFactoryProcessor {
    TestProcessor(final ProcessingEnvironment env) {
      processingEnv = env;
    }
  }

  private static final class TestMessager implements Messager {

    Collection<MessageElements> messages = new HashSet<>();

    @Override
    public void printMessage(final Diagnostic.Kind kind, final CharSequence msg) {
      throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void printMessage(final Diagnostic.Kind kind, final CharSequence msg, final Element e) {
      final MessageElements messageElements = new MessageElements();
      messageElements.kind = kind;
      messageElements.message = msg;
      messageElements.element = e;
      messages.add(messageElements);
    }

    @Override
    public void printMessage(final Diagnostic.Kind kind, final CharSequence msg, final Element e, final AnnotationMirror a) {
      throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void printMessage(final Diagnostic.Kind kind, final CharSequence msg, final Element e, final AnnotationMirror a, final AnnotationValue v) {
      throw new UnsupportedOperationException("Not supported yet.");
    }

    private static final class MessageElements {
      Diagnostic.Kind kind = null;
      CharSequence message = null;
      Element element = null;
      AnnotationMirror mirror = null;
      AnnotationValue value = null;
    }
  }
}