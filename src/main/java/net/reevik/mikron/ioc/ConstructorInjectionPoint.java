/*
 * Copyright (c) 2024 Erhan Bagdemir. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.reevik.mikron.ioc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import net.reevik.mikron.annotation.Wire;

public class ConstructorInjectionPoint<T> implements InjectionPoint {

  private final Constructor<T> constructor;
  private final Wire annotation;
  private final MikronContext context;

  public ConstructorInjectionPoint(Constructor<T> constructor, MikronContext context) {
    this.constructor = constructor;
    this.annotation = constructor.getAnnotation(Wire.class);
    this.context = context;
  }

  @Override
  public Object inject() {
    try {
      Parameter[] parameters = constructor.getParameters();
      List<Object> values = Arrays.stream(parameters).map(this::collect).toList();
      return constructor.newInstance(values.toArray());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Object collect(Parameter p) {
    if (p.isAnnotationPresent(Wire.class)) {

    }
    return null;
  }
}
