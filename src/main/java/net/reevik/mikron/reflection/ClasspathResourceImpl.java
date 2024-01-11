/*
 * Copyright (c) 2023 Erhan Bagdemir. All rights reserved.
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
package net.reevik.mikron.reflection;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.reevik.mikron.annotation.AnnotationResource;

public class ClasspathResourceImpl {
  public static final String DEFAULT_BASE_PKG = "/";

  private final List<Class<?>> repo = Collections.synchronizedList(new ArrayList<>());

  public static ClasspathResourceImpl of(String packageName) {
    return new ClasspathResourceImpl(packageName);
  }

  private ClasspathResourceImpl(String packageName) {
    scan(packageName);
  }

  private void scan(String packageName) {
    var baseDir = Optional.ofNullable(packageName)
        .map(p -> p.replace(".", "/"))
        .orElse(DEFAULT_BASE_PKG);
    var systemClassLoader = ClassLoader.getSystemClassLoader();
    var contextClassLoader = Thread.currentThread().getContextClassLoader();
    scanThroughClassLoaders(baseDir, systemClassLoader);
    scanThroughClassLoaders(baseDir, contextClassLoader);
  }

  private void scan() {
    scan("");
  }

  private void scanThroughClassLoaders(String baseDir, ClassLoader classLoader) {
    try {
      Enumeration<URL> resources = classLoader.getResources(baseDir);
      Iterator<URL> iterator = resources.asIterator();
      while (iterator.hasNext()) {
        var baseURL = iterator.next();
        var protocol = baseURL.getProtocol();
        var packageRoot = new File(baseURL.getFile());
        if (protocol.equals("file") && packageRoot.isDirectory()) {
          File[] files = packageRoot.listFiles();
          Optional.ofNullable(files).ifPresent(fs ->
              Arrays.stream(fs).forEach(file -> process(file, baseDir, classLoader)));
        } else if (protocol.equals("jar")) {
          //TODO
          System.out.println("JAR needs to be exploded:" + baseURL);
        } else {
          throw new IllegalArgumentException("Not a valid package:" + baseURL);
        }
      }

      repo.forEach(System.out::println);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void process(File file, String pkg, ClassLoader classLoader) {
    if (file.isFile() && isClassFile(file)) {
      repo.add(loadClass(file, pkg, classLoader));
      return;
    }

    File[] files = file.listFiles();
    if (files == null) {
      // empty package, or non-class file.
      return;
    }

    var newPkg = pkg.concat(file.getName().concat("."));
    Arrays.stream(files).forEach(child -> {
      if (child.isDirectory()) {
        process(child, newPkg, classLoader);
      } else {
        Class<?> clazz = loadClass(child, newPkg, classLoader);
        if (!repo.contains(clazz)) {
          repo.add(clazz);
        }
      }
    });
  }

  private static Class<?> loadClass(File parent, String pkg, ClassLoader classLoader) {
    try {
      return classLoader.loadClass(pkg.concat(parent.getName().replace(".class", "")));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isClassFile(File parent) {
    return parent.getName().endsWith(".class");
  }

  /**
   * Searches for annotated class instances given.
   *
   * @param annotation Class annotation.
   * @param <T>        The type of annotation.
   * @return List of {@link AnnotationResource} instances.
   */
  public <T extends Annotation> List<AnnotationResource<T>> findClassesBy(Class<T> annotation) {
    List<AnnotationResource<T>> results = new ArrayList<>();
    for (final Class<?> clazz : repo) {
      T annotationOnClass = clazz.getAnnotation(annotation);
      if (annotationOnClass != null) {
        results.add(new AnnotationResource<>(annotationOnClass, clazz));
      }
    }
    return results;
  }

  /**
   * Find annotated classes with annotated fields.
   *
   * @param fieldAnnotation Annotation type for fields.
   * @param classAnnotation Annotation type for classes.
   * @param <T>             Field annotation type.
   * @param <R>             Class annotation type.
   * @return List of fields annotated with field annotation.
   */
  public <T extends Annotation, R extends Annotation> List<Field> findClassesWithFields(
      final Class<T> fieldAnnotation,
      final Class<R> classAnnotation) {
    return findClassesBy(classAnnotation)
        .stream()
        .flatMap(m -> Arrays.stream(m.clazz().getFields())
            .filter(field -> field.isAnnotationPresent(fieldAnnotation)))
        .toList();
  }

  record ClassFile(String packageName, String classFileName) {

  }
}
