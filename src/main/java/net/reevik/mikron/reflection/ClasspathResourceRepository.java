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
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import net.reevik.mikron.annotation.ManagedDefinition;
import net.reevik.mikron.string.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository implementation for classpath. It walks through the system and context
 * {@link ClassLoader} and registers the classes. It provides a public interface which allows
 * clients, for instance, search for class definitions with the annotations given.
 *
 * @author Erhan Bagdemir
 */
public class ClasspathResourceRepository {

  private final static Logger LOG = LoggerFactory.getLogger(ClasspathResourceRepository.class);
  public static final String DEFAULT_BASE_PKG = "/";
  public static final String[] SCAN_ALL = new String[]{".*"};
  private static final String PROTOCOL_FILE = "file";
  private static final String PROTOCOL_JAR = "jar";
  public static final String CLASS_EXT = ".class";

  private final List<Class<?>> repo = Collections.synchronizedList(new ArrayList<>());

  public static ClasspathResourceRepository of(String[] packageName) {
    return new ClasspathResourceRepository(packageName);
  }

  private ClasspathResourceRepository(String[] packageName) {
    scan(packageName);
  }

  private void scan(String[] packageNames) {
    var packages = Arrays.stream(packageNames).collect(Collectors.toList());
    packages.add("net.reevik.mikron.configuration");
    for (final var packageName : packages) {
      checkPackageName(packageName);
      var recursive = packageName.endsWith(".*");
      var baseDir = getPackageToDirectory(packageName);
      var systemClassLoader = ClassLoader.getSystemClassLoader();
      var contextClassLoader = Thread.currentThread().getContextClassLoader();
      scanThroughClassLoaders(baseDir, systemClassLoader, recursive);
      scanThroughClassLoaders(baseDir, contextClassLoader, recursive);
    }
  }

  private void checkPackageName(String packageName) {
    // Not yet implemented.
  }

  private String getPackageToDirectory(String packageName) {
    return Optional.ofNullable(packageName)
        .map(p -> p.replace(".*", ""))
        .map(p -> p.replace(".", "/"))
        .orElse(DEFAULT_BASE_PKG);
  }

  private void scan() {
    scan(SCAN_ALL);
  }

  private void scanThroughClassLoaders(String baseDir, ClassLoader classLoader, boolean recursive) {
    try {
      Enumeration<URL> resources = classLoader.getResources(baseDir);
      Iterator<URL> iterator = resources.asIterator();
      while (iterator.hasNext()) {
        var baseURL = iterator.next();
        var protocol = baseURL.getProtocol();
        var packageRoot = new File(baseURL.getFile());
        if (protocol.equals(PROTOCOL_FILE) && packageRoot.isDirectory()) {
          File[] files = packageRoot.listFiles();
          Optional.ofNullable(files).ifPresent(fs ->
              Arrays.stream(fs).forEach(file -> process(file, baseDir, classLoader, recursive)));
        } else if (protocol.equals(PROTOCOL_JAR)) {
          final Enumeration<JarEntry> entries;
          var path = baseURL.toString();
          var split = path.split("!");
          try (final var jarFile = new JarFile(split[0].replace("jar:file:", ""))) {
            entries = jarFile.entries();
            while (entries.hasMoreElements()) {
              var entry = entries.nextElement();
              var entryName = entry.getName();
              if (entryName.endsWith(".class")) {
                loadClass(entryName, classLoader).ifPresent(repo::add);
              }
            }
          }
        } else {
          throw new IllegalArgumentException("Not a valid package:" + baseURL);
        }
      }
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void process(File file, String baseDir, ClassLoader classLoader, boolean recursive) {
    if (file.isFile() && isClassFile(file)) {
      Class<?> aClass = loadClass(file, baseDir, classLoader);
      if (!repo.contains(aClass)) {
        repo.add(aClass);
      }
      return;
    }

    if (!recursive) {
      return;
    }

    File[] files = file.listFiles();
    if (files == null) {
      // empty package, or non-class file.
      return;
    }

    final var newBaseDir = getNewBaseDir(file, baseDir);
    Arrays.stream(files).forEach(child -> {
      if (child.isDirectory()) {
        process(child, newBaseDir, classLoader, true);
      } else {
        Class<?> clazz = loadClass(child, newBaseDir, classLoader);
        if (!repo.contains(clazz)) {
          repo.add(clazz);
        }
      }
    });
  }

  private String getNewBaseDir(File file, String baseDir) {
    var newBaseDir = baseDir;
    if (!Str.isEmpty(baseDir) && !baseDir.endsWith("/")) {
      newBaseDir += "/";
    }
    newBaseDir += file.getName().concat("/");
    return newBaseDir;
  }

  private Optional<Class<?>> loadClass(String classPath, ClassLoader classLoader) {
    try {
      var fqClass = classPath.replace("/", ".").replace(CLASS_EXT, "");
      return Optional.of(classLoader.loadClass(fqClass));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (NoClassDefFoundError e) {
      LOG.warn("Cannot load the class file: " + classPath, e);
    }
    return Optional.empty();
  }

  private Class<?> loadClass(File parent, String baseDir, ClassLoader classLoader) {
    try {
      var normPkg = (baseDir.endsWith("/") ? baseDir : baseDir.concat("/")).replace("/", ".");
      return classLoader.loadClass(normPkg.concat(parent.getName().replace(CLASS_EXT, "")));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isClassFile(File parent) {
    return parent.getName().endsWith(CLASS_EXT);
  }

  /**
   * Searches for annotated class instances given.
   *
   * @param annotation Class annotation.
   * @param <T>        The type of annotation.
   * @return List of {@link ManagedDefinition} instances.
   */
  public <T extends Annotation> List<ManagedDefinition<T>> findClassesBy(Class<T> annotation) {
    List<ManagedDefinition<T>> results = new ArrayList<>();
    for (final Class<?> clazz : repo) {
      T annotationOnClass = clazz.getAnnotation(annotation);
      if (annotationOnClass != null) {
        results.add(new ManagedDefinition<>(annotationOnClass, clazz));
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

  public <T extends Annotation> Set<Class<?>> findImplementingClasses(Class<?> parentType,
      Class<T> annotation) {
    return findClassesBy(annotation).stream()
        .map(ManagedDefinition::clazz)
        .filter(parentType::isAssignableFrom)
        .collect(Collectors.toSet());
  }

  record ClassFile(String packageName, String classFileName) {

  }
}
