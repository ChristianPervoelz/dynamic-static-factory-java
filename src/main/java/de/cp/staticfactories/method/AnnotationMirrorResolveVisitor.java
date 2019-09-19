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

import java.util.Collection;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Visitor to get the single annotation mirrors in case a method has a @StaticFactoryMethod declared multiple times.
 *
 * @author cperv
 * @version 0.2
 */
final class AnnotationMirrorResolveVisitor implements AnnotationValueVisitor<Object, Collection<AnnotationMirror>> {

  @Override
  public Object visit(final AnnotationValue av, final Collection<AnnotationMirror> p) {
    return av;
  }

  @Override
  public Object visit(final AnnotationValue av) {
    return av;
  }

  @Override
  public Object visitBoolean(final boolean b, final Collection<AnnotationMirror> p) {
    return null;
  }

  @Override
  public Object visitByte(final byte b, final Collection<AnnotationMirror> p) {
    return p;
  }

  @Override
  public Object visitChar(final char c, final Collection<AnnotationMirror> p) {
    return p;
  }

  @Override
  public Object visitDouble(final double d, final Collection<AnnotationMirror> p) {
    return p;
  }

  @Override
  public Object visitFloat(final float f, final Collection<AnnotationMirror> p) {
    return p;
  }

  @Override
  public Object visitInt(final int i, final Collection<AnnotationMirror> p) {
    return p;
  }

  @Override
  public Object visitLong(final long i, final Collection<AnnotationMirror> p) {
    return p;
  }

  @Override
  public Object visitShort(final short s, final Collection<AnnotationMirror> p) {
    return p;
  }

  @Override
  public Object visitString(final String s, final Collection<AnnotationMirror> p) {
    return s;
  }

  @Override
  public Object visitType(final TypeMirror t, final Collection<AnnotationMirror> p) {
    return t;
  }

  @Override
  public Object visitEnumConstant(final VariableElement c, final Collection<AnnotationMirror> p) {
    return c;
  }

  @Override
  public Object visitAnnotation(final AnnotationMirror a, final Collection<AnnotationMirror> p) {
    p.add(a);
    return a;
  }

  @Override
  public Object visitArray(
      final List<? extends AnnotationValue> vals, final Collection<AnnotationMirror> p) {
    // call "this" again to run to the visitAnnotation method
    vals.forEach((val) -> val.accept(this, p));
    return vals;
  }

  @Override
  public Object visitUnknown(final AnnotationValue av, final Collection<AnnotationMirror> p) {
    return av;
  }

}
