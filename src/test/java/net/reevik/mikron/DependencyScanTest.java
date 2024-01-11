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
import java.util.Optional;
import net.reevik.mikron.ioc.MikronContext;
import net.reevik.mikron.ioc.MikronContext.ManagedInstance;
import net.reevik.mikron.annotation.AnnotationResource;
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.reflection.ClasspathResourceImpl;
import org.junit.jupiter.api.Test;

public class DependencyScanTest {

  @Test
  void testScanClasses() {
    ClasspathResourceImpl dependencyScan = ClasspathResourceImpl.of("");
    List<AnnotationResource<Managed>> by = dependencyScan.findClassesBy(Managed.class);
    assertThat(by).hasSize(2);
  }

  @Test
  void testMikronContext() {
    MikronContext context = MikronContext.init(DependencyScanTest.class);
    Map<String, ManagedInstance> managedInstances = context.getInstanceCache();
    assertThat(managedInstances).hasSize(2);
    Optional<ManagedInstance> first = managedInstances.values().stream().findFirst();
    assertThat(first.get().instance()).isInstanceOf(AnnotatedTestClass.class);
  }
}
