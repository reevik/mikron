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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.reevik.mikron.annotation.AnnotationResource;
import net.reevik.mikron.annotation.Configurable;
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.ManagedApplication;
import net.reevik.mikron.annotation.Wire;
import net.reevik.mikron.configuration.ConfigurationBinding;
import net.reevik.mikron.configuration.PropertiesRepository;
import net.reevik.mikron.configuration.TypeConverter;
import net.reevik.mikron.reflection.ClasspathResourceRepository;
import net.reevik.mikron.string.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mikron context is the inversion-of-control container, which control the life-cycle of the mikron
 * managed instances. {@link MikronContext} instance is managed itself, even though it's not
 * annotated with {@link Managed} so it can be injected in your application code.
 *
 * @author Erhan Bagdemir
 */
public class MikronContext {

  private final static Logger LOG = LoggerFactory.getLogger(MikronContext.class);

  /**
   * The cache for the managed instances.
   */
  private final Map<String, ManagedInstance> managedInstances = new HashMap<>();

  private final PropertiesRepository propertiesRepository;

  private final Class<?> applicationClass;

  @Configurable(name = "key")
  private int key;

  private MikronContext(Class<?> applicationClass) {
    this.propertiesRepository = new PropertiesRepository();
    this.propertiesRepository.loadAllProperties();
    this.applicationClass = applicationClass;
  }

  public synchronized static MikronContext init(Class<?> clazz) {
    var managedContext = new MikronContext(clazz);
    managedContext.managedInstances.clear();
    managedContext.initializeContext();
    managedContext.wireConfigurations();
    return managedContext;
  }

  /**
   * Registers a new managed instance explicitly, which makes the context rescan the packages so
   * that the new managed instance can be wired.
   * <p/>
   *
   * @param instance Which is registered manually in the current context.
   * @param name     The managedInstanceName of the managed instance.
   */
  public void register(Object instance, String name) {
    managedInstances.clear();
    managedInstances.put(name, new ManagedInstance(null, instance, name, this));
    initializeContext();
    wireConfigurations();
  }

  private void wireConfigurations() {
    managedInstances.values().forEach(ManagedInstance::configSetup);
  }

  private void initializeContext() {
    var classpath = initializeClasspath(applicationClass);
    var instances = managedInstances;
    // Make MikronContext wire-able like any other managed instances.
    registerSelf(instances);

    for (var annotationResource : classpath.findClassesBy(Managed.class)) {
      var componentName = getName(annotationResource);
      var propBasedInstanceCreation = false;
      for (var propFile : propertiesRepository.getPropertyClassNames()) {
        if (propFile.startsWith(componentName) && !instances.containsKey(propFile)) {
          instances.put(propFile, initObject(annotationResource, propFile));
          propBasedInstanceCreation = true;
        }
      }
      if (!propBasedInstanceCreation) {
        instances.put(componentName, initObject(annotationResource, componentName));
      }
    }
    instances.values().forEach(ManagedInstance::wire);
  }

  private void registerSelf(Map<String, ManagedInstance> instances) {
    instances.put(MikronContext.class.getSimpleName(),
        new ManagedInstance(null, this, MikronContext.class.getSimpleName(), this));
  }

  private String getName(AnnotationResource<Managed> annotationResource) {
    var name = annotationResource.annotation().name();
    if (Str.isEmpty(name)) {
      return annotationResource.clazz().getName();
    }
    return name;
  }

  private ClasspathResourceRepository initializeClasspath(Class<?> clazz) {
    final ClasspathResourceRepository classpath;
    final var declaredAnnotation = clazz.getAnnotation(ManagedApplication.class);
    if (declaredAnnotation == null) {
      throw new ApplicationInitializationException("No managed application found with "
          + "@ManagedApplication annotation.");
    }
    classpath = ClasspathResourceRepository.of(declaredAnnotation.packages());
    return classpath;
  }

  private ManagedInstance initObject(AnnotationResource<Managed> annotationResource, String name) {
    try {
      var clazz = annotationResource.clazz();
      var constructor = clazz.getConstructor();
      return new ManagedInstance(annotationResource, constructor.newInstance(), name, this);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
             NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, ManagedInstance> getManagedInstances() {
    return managedInstances;
  }

  public <T> Optional<T> getInstance(String name) {
    if (managedInstances.containsKey(name)) {
      ManagedInstance managedInstance = managedInstances.get(name);
      return Optional.of((T) managedInstance.instance);
    }
    return Optional.empty();
  }

  public PropertiesRepository getPropertiesRepository() {
    return propertiesRepository;
  }

  public record ManagedInstance(AnnotationResource<Managed> annotationResource,
                                Object instance,
                                String managedInstanceName,
                                MikronContext context) {

    public void wire() {
      Arrays.stream(instance.getClass().getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(Wire.class))
          .forEach(this::wireDependency);
    }

    public void configSetup() {
      Arrays.stream(instance.getClass().getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(Configurable.class))
          .forEach(this::wireConfiguration);
    }

    private void wireDependency(final Field field) {
      var wireAnnotation = field.getAnnotation(Wire.class);
      var classKey = getDependencyName(field, wireAnnotation);
      var filter = wireAnnotation.filter();
      bindDependency(field, classKey, filter);
    }

    private void bindDependency(Field field, String classKey, String filter) {
      try {
        var propClassName = getPropClassNameByFilter(classKey, filter);
        if (field.trySetAccessible() && context.getManagedInstances().containsKey(propClassName)) {
          var managedInstance = context.getManagedInstances().get(propClassName);
          field.setAccessible(true);
          field.set(instance, managedInstance.instance);
        }
      } catch (IllegalAccessException e) {
        LOG.error("Cannot wire the field={} Reason={}", classKey, e.getMessage());
      } catch (IllegalArgumentException e) {
        throw new DependencyWiringException(e);
      }
    }

    private String getPropClassNameByFilter(String classKey, String filter) {
      if (Str.isEmpty(filter)) {
        return classKey;
      }
      var filterArr = filter.split("=");
      var filterName = filterArr[0];
      var filterValue = filterArr[1];
      var propRepo = context.getPropertiesRepository();
      return propRepo.getPropertyClassNames().stream()
          .filter(className -> className.startsWith(classKey))
          .filter(className -> filterValue.equals(
              propRepo.getConfiguration(className).map(b -> b.get(filterName)).orElse(null)))
          .findFirst().orElse(classKey);
    }

    private void wireConfiguration(final Field field) {
      bindConfig(field, getConfigName(field));
    }

    private void bindConfig(Field field, String propName) {
      try {
        if (field.trySetAccessible()) {
          var managedConfig = context.getPropertiesRepository().getConfiguration(
              managedInstanceName);
          var converter = field.getAnnotation(Configurable.class).converter();
          var bindingInstance = context.getConverter(field.getType(), converter);
          var targetVal = bindingInstance.convert(managedConfig
              .map(g -> g.get(propName))
              .orElse(null));
          new ConfigurationBinding().bind(field, instance, targetVal);
        }
      } catch (IllegalAccessException e) {
        LOG.error("Cannot wire the field={} Reason={}", propName, e.getMessage());
      } catch (InvocationTargetException | NoSuchMethodException | InstantiationException e) {
        throw new RuntimeException(e);
      }
    }

    // Properties managedInstanceName is used to associate the property file managedInstanceName and the managed class.
    private String getPropertiesName() {
      String propsClassName;
      // Normally, managed resources in instance cache aren't expected to be null.
      // Except the case that if a managed object is manually added to the cache, for example,
      // MikronContext itself.
      if (annotationResource != null) {
        // Properties class managedInstanceName can be provided in the @Managed annotation. It has precedence.
        propsClassName = annotationResource.annotation().name();
        if (Str.isEmpty(propsClassName)) {
          // Otherwise, we use the simple managedInstanceName of the class.
          propsClassName = annotationResource.clazz().getSimpleName();
        }
      } else {
        // If the registered annotationResource is null, then we loop up for the properties with the simple
        // class managedInstanceName of the instance.
        propsClassName = instance.getClass().getSimpleName();
      }

      return propsClassName;
    }

    private String getConfigName(Field field) {
      var annotation = field.getAnnotation(Configurable.class);
      var name = annotation.name();
      return Str.isEmpty(name) ? field.getType().getName() : name;
    }

    private String getDependencyName(Field field, Wire annotation) {
      var name = annotation.name();
      return Str.isEmpty(name) ? field.getType().getName() : name;
    }
  }

  private TypeConverter getConverter(Class<?> clazz,
      Class<? extends TypeConverter> converter)
      throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    try {
      return converter.getConstructor(Class.class).newInstance(clazz);
    } catch (NoSuchMethodException nsm) {
      return converter.getConstructor().newInstance();
    }
  }
}

