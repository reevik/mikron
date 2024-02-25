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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Predicate;
import net.reevik.mikron.annotation.AnnotationResource;
import net.reevik.mikron.annotation.Configurable;
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.Wire;
import net.reevik.mikron.configuration.ConfigurationBinding;
import net.reevik.mikron.configuration.PropertiesRepository;
import net.reevik.mikron.string.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagedInstance {

  private final static Logger LOG = LoggerFactory.getLogger(ManagedInstance.class);

  private final AnnotationResource<Managed> annotationResource;
  private final Object instance;
  private final String managedInstanceName;
  private final MikronContext context;

  public ManagedInstance(AnnotationResource<Managed> annotationResource, Object instance,
      String managedInstanceName, MikronContext context) {
    this.annotationResource = annotationResource;
    this.instance = instance;
    this.managedInstanceName = managedInstanceName;
    this.context = context;
  }

  public void wire() {
    Arrays.stream(instance.getClass().getDeclaredFields())
        .filter(field -> field.isAnnotationPresent(Wire.class)).forEach(this::wireDependency);
  }

  private void wireDependency(Field field) {
    switch (field.getAnnotation(Wire.class).scope()) {
      case STATIC -> bindStatic(field);
      case ACCESS -> bindOnAccess(field);
    }
  }

  private void bindStatic(Field field) {
    try {
      var wire = field.getAnnotation(Wire.class);
      var componentNameOnField = getDependencyName(field, wire);
      var propClassName = getComponentName(componentNameOnField, wire.filter());
      if (field.trySetAccessible() && context.getManagedInstances().containsKey(propClassName)) {
        var managedInstance = context.getManagedInstances().get(propClassName);
        field.setAccessible(true);
        field.set(instance, managedInstance.getInstance());
      }
    } catch (IllegalAccessException e) {
      LOG.error("Cannot wire the field={} Reason={}", field, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new DependencyWiringException(e);
    }
  }

  private void bindOnAccess(Field field) {
    try {
      var type = field.getType();
      var wire = field.getAnnotation(Wire.class);
      var componentNameOnField = getDependencyName(field, wire);
      var propClassName = getComponentName(componentNameOnField, wire.filter());
      var proxyInstance = Proxy.newProxyInstance(
          ManagedInstance.class.getClassLoader(),
          new Class[]{type},
          new DynamicWiringInvocation(type, context, wire.name(), propClassName));
      field.setAccessible(true);
      field.set(instance, proxyInstance);
    } catch (IllegalAccessException e) {
      LOG.error("Cannot wire the field={} Reason={}", field, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new DependencyWiringException(e);
    }
  }

  private String getComponentName(String classKey, String propertyFilter) {
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
        .map(b -> b.get(filterName)).orElse(null));
  }

  public void configSetup() {
    configSetup(managedInstanceName);
  }

  public void configSetup(String configurationSourceKey) {
    Arrays.stream(instance.getClass().getDeclaredFields())
        .filter(field -> field.isAnnotationPresent(Configurable.class))
        .forEach(field -> wireConfiguration(field, configurationSourceKey));
  }

  private void wireConfiguration(final Field field, String configurationSourceKey) {
    bindConfig(field, getConfigNameOnField(field), configurationSourceKey);
  }

  private void bindConfig(Field field, String configName, String configurationSourceKey) {
    try {
      if (field.trySetAccessible()) {
        var managedConfig = context.getConfiguration(configurationSourceKey);
        var converter = field.getAnnotation(Configurable.class).converter();
        var bindingInstance = context.getConverter(field.getType(), converter);
        var targetVal = bindingInstance.convert(
            managedConfig.map(g -> g.get(configName)).orElse(null));
        new ConfigurationBinding().bind(field, instance, targetVal);
      }
    } catch (IllegalAccessException e) {
      LOG.error("Cannot wire the field={} Reason={}", configName, e.getMessage());
    } catch (InvocationTargetException | NoSuchMethodException | InstantiationException e) {
      throw new RuntimeException(e);
    }
  }

  private String getConfigNameOnField(Field field) {
    var annotation = field.getAnnotation(Configurable.class);
    var name = annotation.name();
    return Str.isEmpty(name) ? field.getType().getName() : name;
  }

  private String getDependencyName(Field field, Wire annotation) {
    var name = annotation.name();
    return Str.isEmpty(name) ? field.getType().getName() : name;
  }

  public AnnotationResource<Managed> getAnnotationResource() {
    return annotationResource;
  }

  public Object getInstance() {
    return instance;
  }

  public String getManagedInstanceName() {
    return managedInstanceName;
  }
}
