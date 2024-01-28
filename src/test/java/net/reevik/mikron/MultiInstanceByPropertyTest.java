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

import java.util.Optional;
import net.reevik.mikron.annotation.ManagedApplication;
import net.reevik.mikron.ioc.MikronContext;
import net.reevik.mikron.test.AnnotatedTestClass;
import net.reevik.mikron.test6.MultiInstanceByProperty;
import net.reevik.mikron.test6.SelectiveWiring;
import org.junit.jupiter.api.Test;

@ManagedApplication(packages = {"net.reevik.mikron.test6"})
public class MultiInstanceByPropertyTest {

  @Test
  void testMultiInstanceSelectiveBinding() {
    var context = MikronContext.init(MultiInstanceByPropertyTest.class);
    Optional<SelectiveWiring> maybeIut = context.getInstance("SelectiveWiring");
    assertThat(maybeIut).isPresent();
    var iut = maybeIut.get();
    assertThat(iut).isNotNull();
    MultiInstanceByProperty byProperty = iut.getSelectiveWiring();
    assertThat(byProperty).isNotNull();
    assertThat(byProperty.getName()).isEqualTo("instance1");

  }
}
