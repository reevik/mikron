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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import net.reevik.mikron.annotation.ManagedDefinition;
import net.reevik.mikron.annotation.CleanUp;
import net.reevik.mikron.annotation.Configurable;
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.ManagedApplication;
import net.reevik.mikron.annotation.Initialize;
import net.reevik.mikron.annotation.Prefer;
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
public class MikronContext implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(MikronContext.class);

  /**
   * The cache for the managed instances.
   */
  private final Map<String, ManagedInstance> managedInstances = new HashMap<>();
  private final PropertiesRepository propertiesRepository;
  private final ClasspathResourceRepository classpathResourceRepository;

  @Configurable(name = "key")
  private int key;

  private MikronContext(Class<?> applicationClass) {
    this.propertiesRepository = new PropertiesRepository();
    this.propertiesRepository.loadAllProperties();
    this.classpathResourceRepository = initializeClasspath(applicationClass);
  }

  public static MikronContext init(Class<?> clazz) {
    var managedContext = new MikronContext(clazz);
    managedContext.managedInstances.clear();
    managedContext.initializeContext();
    managedContext.initializeConfigurations();
    return managedContext;
  }

  /**
   * Registers a new managed instance explicitly, which makes the context rescan the packages so
   * that the new managed instance can be wired.
   * <p>
   *
   * @param instance Which is registered manually in the current context.
   * @param name     The managedInstanceName of the managed instance.
   */
  public void register(Object instance, String name) {
    managedInstances.clear();
    managedInstances.put(name, new ManagedInstance(instance, name, this));
    initializeContext();
    initializeConfigurations();
    postConstruct();
  }

  private void initializeConfigurations() {
    managedInstances.values().forEach(ManagedInstance::configSetup);
  }

  private void initializeContext() {
    // Make "MikronContext" wireable like any other developer managed instances.
    registerSelf();
    for (var annotationResource : classpathResourceRepository.findClassesBy(Managed.class)) {
      var componentName = getName(annotationResource);
      var propBasedInstanceCreation = createInstancePerPropertyFile(annotationResource,
          componentName);
      // It is possible that there is no configuration file at all, so we need to instantiate the
      // component by name.
      if (!propBasedInstanceCreation) {
        managedInstances.put(componentName, initObject(annotationResource, componentName));
      }
    }
    managedInstances.values().forEach(ManagedInstance::wire);
  }

  private boolean createInstancePerPropertyFile(ManagedDefinition<Managed> annotationResource,
      String componentName) {
    var propBasedInstanceCreation = false;
    for (var propFile : propertiesRepository.getPropertyClassNames()) {
      if (propFile.startsWith(componentName) && !managedInstances.containsKey(propFile)) {
        managedInstances.put(propFile, initObject(annotationResource, propFile));
        propBasedInstanceCreation = true;
      }
    }
    return propBasedInstanceCreation;
  }

  private void registerSelf() {
    managedInstances.put(MikronContext.class.getSimpleName(),
        new ManagedInstance(this, MikronContext.class.getSimpleName(), this));
  }

  private String getName(ManagedDefinition<Managed> annotationResource) {
    var name = annotationResource.annotation().name();
    if (Str.isEmpty(name)) {
      return annotationResource.clazz().getName();
    }
    return name;
  }

  private ClasspathResourceRepository initializeClasspath(Class<?> clazz) {
    final var declaredAnnotation = clazz.getAnnotation(ManagedApplication.class);
    if (declaredAnnotation == null) {
      throw new ApplicationInitializationException(
          "No managed application found with @ManagedApplication annotation.");
    }
    return ClasspathResourceRepository.of(declaredAnnotation.packages());
  }

  private ManagedInstance initObject(ManagedDefinition<Managed> annotationResource, String name) {
    Class<?> managedDefiningClass = annotationResource.clazz();
    Constructor<?>[] constructors = managedDefiningClass.getConstructors();
    return Arrays.stream(constructors)
        .filter(constructor -> constructor.isAnnotationPresent(Prefer.class))
        .findFirst()
        .map(constructor -> new ConstructorInjectionPoint<>(constructor, this).inject())
        .map(managedInstance -> new ManagedInstance(managedInstance, name, this))
        .orElseGet(() -> instantiateByDefault(annotationResource, name));
  }

  private ManagedInstance instantiateByDefault(ManagedDefinition<Managed> annotationResource,
      String name) {
    try {
      var clazz = annotationResource.clazz();
      var constructor = clazz.getConstructor();
      return new ManagedInstance(constructor.newInstance(), name, this);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  ManagedInstance initObjectByAccess(Class<?> targetType, String targetName) {
    try {
      if (!targetType.isInterface() && !targetType.isAnnotationPresent(Managed.class)) {
        throw new IllegalWiringException("You can only wire managed objects with @Managed "
            + "annotation on them.");
      }

      Class<?> managedType = null;
      if (targetType.isInterface()) {

        Set<Class<?>> implementingManagedInstances = findImplementingManagedInstances(targetType);
        if (implementingManagedInstances.size() == 1 && Str.isEmpty(targetName)) {
          managedType = implementingManagedInstances.iterator().next();
        }

        if (implementingManagedInstances.size() >= 1 && Str.isNotEmpty(targetName)) {
          Optional<Class<?>> matchingNamedManagedInstance =
              implementingManagedInstances.stream().filter(managedClass ->
                  targetName.equals(managedClass.getAnnotation(Managed.class).name())).findFirst();
          managedType =
              matchingNamedManagedInstance.orElseThrow(MatchingDependencyNotFoundException::new);
        }

        if (managedType == null) {
          throw new MatchingDependencyNotFoundException();
        }

      } else {
        managedType = targetType;
      }
      var constructor = managedType.getConstructor();
      return new ManagedInstance(constructor.newInstance(), targetName, this);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, ManagedInstance> getManagedInstances() {
    return managedInstances;
  }

  public <T> Optional<T> getInstance(String name) {
    if (managedInstances.containsKey(name)) {
      ManagedInstance managedInstance = managedInstances.get(name);
      return Optional.of((T) managedInstance.getInstance());
    }
    return Optional.empty();
  }

  public PropertiesRepository getPropertiesRepository() {
    return propertiesRepository;
  }

  public Optional<Properties> getConfiguration(String configurationSourceKey) {
    return propertiesRepository.getConfiguration(configurationSourceKey);
  }

  private void postConstruct() {
    executeIfAnnotated(Initialize.class);
  }

  @Override
  public void close() throws Exception {
    executeIfAnnotated(CleanUp.class);
  }

  private void executeIfAnnotated(Class<? extends Annotation> annotation) {
    for (var managedInstance : managedInstances.values()) {
      Class<?> aClass = managedInstance.getInstance().getClass();
      for (Method declaredMethod : aClass.getDeclaredMethods()) {
        if (declaredMethod.isAnnotationPresent(annotation)) {
          try {
            if (declaredMethod.getParameterCount() > 0) {
              throw new IllegalArgumentException(
                  "@Initialize/@CleanUp methods shouldn't take " + "parameters.");
            }
            declaredMethod.invoke(managedInstance.getInstance());
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
  }

  /**
   * Find and return the implementing managed instances.
   *
   * @param parentType Parent type, e.g., an interface declaring a concrete managed instance.
   * @return All implementing concrete managed instances.
   */
  public Set<Class<?>> findImplementingManagedInstances(Class<?> parentType) {
    return classpathResourceRepository.findImplementingClasses(parentType, Managed.class);
  }

  TypeConverter getConverter(Class<?> clazz, Class<? extends TypeConverter> converter)
      throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    try {
      return converter.getConstructor(Class.class).newInstance(clazz);
    } catch (NoSuchMethodException nsm) {
      return converter.getConstructor().newInstance();
    }
  }
}

