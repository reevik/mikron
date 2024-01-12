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
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.ManagedApplication;
import net.reevik.mikron.annotation.Wire;
import net.reevik.mikron.configuration.PropertiesRepository;
import net.reevik.mikron.reflection.ClasspathResourceImpl;
import net.reevik.mikron.string.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mikron context is the inversion-of-control container, which control the life-cycle of the
 * mikron managed instances.
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

  private final ClasspathResourceImpl classpath;

  @Wire
  private PropertiesRepository propertiesRepository;

  private MikronContext(Class<?> clazz) {
    ManagedApplication declaredAnnotation = clazz.getAnnotation(ManagedApplication.class);
    String[] packages = declaredAnnotation.packages();
    classpath = ClasspathResourceImpl.of(packages);
  }

  public static MikronContext init(Class<?> clazz) {
    if (INSTANCE == null) {
      INSTANCE = new MikronContext(clazz);
      final var instances = INSTANCE.managedInstances;
      instances.put(MikronContext.class.getName(), new ManagedInstance(null, INSTANCE));
      final var annotationResources = INSTANCE.classpath.findClassesBy(Managed.class);
      annotationResources.forEach(r -> instances.put(getName(r), INSTANCE.initObject(r)));
      instances.values().forEach(ManagedInstance::wire);
    }
    return INSTANCE;
  }

  private static String getName(AnnotationResource<Managed> annotationResource) {
    var name = annotationResource.annotation().name();
    if (Str.isEmpty(name)) {
      return annotationResource.clazz().getName();
    }
    return name;
  }

  private ManagedInstance initObject(AnnotationResource<Managed> annotationResource) {
    try {
      var clazz = annotationResource.clazz();
      var constructor = clazz.getConstructor();
      return new ManagedInstance(annotationResource, constructor.newInstance());
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

  public record ManagedInstance(AnnotationResource<Managed> resource, Object instance) {

    public void wire() {
      Arrays.stream(instance.getClass().getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(Wire.class)).forEach(this::wireField);
    }

    private void wireField(final Field field) {
      var annotation = field.getAnnotation(Wire.class);
      if (annotation != null) {
        var key = getDependencyName(field, annotation);

        try {
          if (field.trySetAccessible() && INSTANCE.managedInstances.containsKey(key)) {
            var managedInstance = INSTANCE.managedInstances.get(key);
            field.setAccessible(true);
            field.set(instance, managedInstance.instance);
          }
        } catch (IllegalAccessException e) {
          LOG.error("Cannot wire the field={} Reason={}", key, e.getMessage());
        }
      }
    }

    private String getDependencyName(Field field, Wire annotation) {
      var name = annotation.name();
      return Str.isEmpty(name) ? field.getType().getName() : name;
    }
  }
}
