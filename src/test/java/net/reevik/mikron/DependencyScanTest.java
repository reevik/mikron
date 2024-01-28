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
package net.reevik.mikron;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.reevik.mikron.annotation.AnnotationResource;
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.ManagedApplication;
import net.reevik.mikron.configuration.PropertiesRepository;
import net.reevik.mikron.ioc.MikronContext;
import net.reevik.mikron.ioc.MikronContext.ManagedInstance;
import net.reevik.mikron.reflection.ClasspathResourceRepository;
import net.reevik.mikron.test.AnnotatedDependencyTestClass;
import net.reevik.mikron.test.AnnotatedTestClass;
import org.junit.jupiter.api.Test;

@ManagedApplication(packages = {"net.reevik.mikron.test"})
public class DependencyScanTest {

  @Test
  void testScanAllClasses() {
    ClasspathResourceRepository dependencyScan = ClasspathResourceRepository.of(
        ClasspathResourceRepository.SCAN_ALL);
    List<AnnotationResource<Managed>> by = dependencyScan.findClassesBy(Managed.class);
    assertThat(by).hasSize(10);
  }

  @Test
  void testMikronContext_nonRecursive() {
    MikronContext context = MikronContext.init(DependencyScanTest.class);
    Map<String, ManagedInstance> managedInstances = context.getManagedInstances();
    assertThat(managedInstances).hasSize(3);
    assertThat(managedInstances.containsKey(AnnotatedTestClass.class.getName())).isTrue();
    assertThat(managedInstances.containsKey(AnnotatedDependencyTestClass.class.getName())).isTrue();
    assertThat(managedInstances.containsKey(MikronContext.class.getSimpleName())).isTrue();
  }
}
