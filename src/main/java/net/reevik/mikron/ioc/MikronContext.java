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
import net.reevik.mikron.annotation.Wire;
import net.reevik.mikron.reflection.ClasspathResourceImpl;
import net.reevik.mikron.string.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MikronContext {

  private final static Logger LOG = LoggerFactory.getLogger(MikronContext.class);

  private static MikronContext INSTANCE;

  private final Map<String, ManagedInstance> instanceCache = new HashMap<>();

  private final ClasspathResourceImpl classpath = ClasspathResourceImpl.of("");

  private MikronContext() {
  }

  public static MikronContext init(Class<?> clazz) {
    if (INSTANCE == null) {
      INSTANCE = new MikronContext();
      var annotationResources = INSTANCE.classpath.findClassesBy(Managed.class);
      annotationResources.forEach(r ->
          INSTANCE.instanceCache.put(getName(r),
          INSTANCE.initObject(r)));
      INSTANCE.instanceCache.values().forEach(ManagedInstance::wire);
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

  public Map<String, ManagedInstance> getInstanceCache() {
    return instanceCache;
  }

  public <T> Optional<T> getInstance(String name) {
    if (INSTANCE.instanceCache.containsKey(name)) {
      ManagedInstance managedInstance = INSTANCE.instanceCache.get(name);
      return Optional.of((T) managedInstance.instance);
    }
    return Optional.empty();
  }

  public record ManagedInstance(AnnotationResource<Managed> resource, Object instance) {

    public void wire() {
      Arrays.stream(instance.getClass().getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(Wire.class))
          .forEach(this::wireField);
    }

    private void wireField(final Field field) {
      var annotation = field.getAnnotation(Wire.class);
      if (annotation != null) {
        var key = getDependencyName(field, annotation);

        try {
          if (field.trySetAccessible()) {
            var managedInstance = INSTANCE.instanceCache.get(key);
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
