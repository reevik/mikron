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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
   * Singleton instance of the {@link MikronContext}.
   */
  private static MikronContext INSTANCE;

  /**
   * The cache for the managed instances.
   */
  private final Map<String, ManagedInstance> managedInstances = new HashMap<>();

  private final PropertiesRepository propertiesRepository;

  @Configurable(name = "key")
  private int key;

  private MikronContext() {
    propertiesRepository = new PropertiesRepository();
    propertiesRepository.loadAllProperties();
  }

  public synchronized static MikronContext init(Class<?> clazz) {
    if (INSTANCE == null) {
      INSTANCE = new MikronContext();
    }
    INSTANCE.managedInstances.clear();
    INSTANCE.initializeContext(clazz);
    INSTANCE.wireConfigurations();
    return INSTANCE;
  }

  private void wireConfigurations() {
    INSTANCE.managedInstances.values().forEach(ManagedInstance::configSetup);
  }

  private void initializeContext(Class<?> clazz) {
    final var classpath = INSTANCE.initializeClasspath(clazz);
    final var instances = INSTANCE.managedInstances;
    instances.put(MikronContext.class.getSimpleName(),
        new ManagedInstance(null, INSTANCE, MikronContext.class.getSimpleName()));
    for (var annoRes : classpath.findClassesBy(Managed.class)) {
      var componentName = INSTANCE.getName(annoRes);
      var propBasedInstanceCreation = false;
      for (var propFile : propertiesRepository.getPropertyClassNames()) {
        if (propFile.startsWith(componentName) && !instances.containsKey(propFile)) {
          instances.put(propFile, INSTANCE.initObject(annoRes, propFile));
          propBasedInstanceCreation = true;
        }
      }
      if (!propBasedInstanceCreation) {
        instances.put(componentName, INSTANCE.initObject(annoRes, componentName));
      }
    }
    instances.values().forEach(ManagedInstance::wire);
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
      return new ManagedInstance(annotationResource, constructor.newInstance(), name);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
             NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, ManagedInstance> getManagedInstances() {
    return managedInstances;
  }

  public <T> Optional<T> getInstance(String name) {
    if (INSTANCE.managedInstances.containsKey(name)) {
      ManagedInstance managedInstance = INSTANCE.managedInstances.get(name);
      return Optional.of((T) managedInstance.instance);
    }
    return Optional.empty();
  }

  public record ManagedInstance(AnnotationResource<Managed> resource, Object instance,
                                String name) {

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
        if (field.trySetAccessible() && INSTANCE.managedInstances.containsKey(propClassName)) {
          var managedInstance = INSTANCE.managedInstances.get(propClassName);
          field.setAccessible(true);
          field.set(instance, managedInstance.instance);
        }
      } catch (IllegalAccessException e) {
        LOG.error("Cannot wire the field={} Reason={}", classKey, e.getMessage());
      } catch (IllegalArgumentException e) {
        throw new DependencyWiringException(e);
      }
    }

    private static String getPropClassNameByFilter(String classKey, String filter) {
      if (Str.isEmpty(filter)) {
        return classKey;
      }
      var filterArr = filter.split("=");
      var filterName = filterArr[0];
      var filterValue = filterArr[1];
      final var propRepo = INSTANCE.propertiesRepository;
      final var propClassName = propRepo.getPropertyClassNames().stream()
          .filter(className -> className.startsWith(classKey))
          .filter(className -> filterValue.equals(
              propRepo.getConfiguration(className).map(b -> b.get(filterName)).orElse(null)))
          .findFirst().orElse(classKey);
      return propClassName;
    }

    private void wireConfiguration(final Field field) {
      bindConfig(field, getConfigName(field));
    }

    private void bindConfig(Field field, String propName) {
      try {
        if (field.trySetAccessible()) {
          var managedConfig = INSTANCE.propertiesRepository.getConfiguration(name);
          var converter = field.getAnnotation(Configurable.class).converter();
          var bindingInstance = INSTANCE.getConverter(field.getType(), converter);
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

    // Properties name is used to associate the property file name and the managed class.
    private String getPropertiesName() {
      String propsClassName;
      // Normally, managed resources in instance cache aren't expected to be null.
      // Except the case that if a managed object is manually added to the cache, for example,
      // MikronContext itself.
      if (resource != null) {
        // Properties class name can be provided in the @Managed annotation. It has precedence.
        propsClassName = resource.annotation().name();
        if (Str.isEmpty(propsClassName)) {
          // Otherwise, we use the simple name of the class.
          propsClassName = resource.clazz().getSimpleName();
        }
      } else {
        // If the registered resource is null, then we loop up for the properties with the simple
        // class name of the instance.
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

