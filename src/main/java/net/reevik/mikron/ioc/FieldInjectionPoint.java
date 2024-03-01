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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Predicate;
import net.reevik.mikron.annotation.Dynamic;
import net.reevik.mikron.annotation.Wire;
import net.reevik.mikron.configuration.PropertiesRepository;
import net.reevik.mikron.string.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FieldInjectionPoint implements InjectionPoint {
  private final static Logger LOG = LoggerFactory.getLogger(FieldInjectionPoint.class);

  private final Field field;
  private final Wire annotation;
  private final MikronContext context;
  private final Object self;

  public FieldInjectionPoint(Object self, Field field, MikronContext context) {
    this.field = field;
    this.annotation = field.getAnnotation(Wire.class);
    this.context = context;
    this.self = self;
  }

  @Override
  public Object inject() {
    if (field.isAnnotationPresent(Dynamic.class)) {
      dynamicWiring();
      return self;
    }
    staticWiring();
    return self;
  }

  private void staticWiring() {
    try {
      var propClassName = getComponentName();
      if (field.trySetAccessible()) {
        if (context.getManagedInstances().containsKey(propClassName)) {
          var managedInstance = context.getManagedInstances().get(propClassName);
          field.set(self, managedInstance.getInstance());
        } else {
          injectFirstAssignable(field);
        }
      }
    } catch (IllegalAccessException | IllegalArgumentException e) {
      throw new DependencyWiringException(e);
    }
  }

  private void dynamicWiring() {
    try {
      var type = field.getType();
      var wire = field.getAnnotation(Wire.class);
      var propClassName = getComponentName();
      var proxyInstance = Proxy.newProxyInstance(
          ManagedInstance.class.getClassLoader(),
          new Class[]{type},
          new DynamicWiringInvocation(type, context, wire.name(), propClassName));
      field.setAccessible(true);
      field.set(self, proxyInstance);
    } catch (IllegalAccessException e) {
      LOG.error("Cannot wire the field={} Reason={}", field, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new DependencyWiringException(e);
    }
  }


  private void injectFirstAssignable(Field field) throws IllegalAccessException {
    Class<?> type = field.getType();
    List<ManagedInstance> candidates = context.getManagedInstances()
        .values().stream()
        .filter(s -> type.isAssignableFrom(s.getInstance().getClass()))
        .toList();
    setIfNotAmbiguous(field, candidates);
  }

  private void setIfNotAmbiguous(Field field, List<ManagedInstance> candidates)
      throws IllegalAccessException {
    if (candidates.size() == 1) {
      ManagedInstance firstCandidate = candidates.iterator().next();
      field.set(self, firstCandidate.getInstance());
    }
  }

  private String getComponentName() {
    var classKey = getDependencyName();
    var propertyFilter = annotation.filter();
    if (Str.isEmpty(propertyFilter)) {
      return classKey;
    }
    var filterArr = propertyFilter.split("=");
    var filterName = filterArr[0];
    var filterValue = filterArr[1];
    var propRepo = context.getPropertiesRepository();
    return propRepo.getPropertyClassNames().stream()
        .filter(className -> className.startsWith(classKey))
        .filter(hasFilteredProperty(filterName, filterValue, propRepo))
        .findFirst()
        .orElse(classKey);
  }

  private Predicate<String> hasFilteredProperty(String filterName, String filterValue,
      PropertiesRepository propRepo) {
    return className -> filterValue.equals(propRepo.getConfiguration(className)
        .map(b -> b.get(filterName))
        .orElse(null));
  }

  private String getDependencyName() {
    var name = annotation.name();
    return Str.isEmpty(name) ? field.getType().getName() : name;
  }
}
