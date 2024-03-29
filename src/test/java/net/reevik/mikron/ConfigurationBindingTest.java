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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Optional;
import net.reevik.mikron.annotation.ManagedApplication;
import net.reevik.mikron.ioc.MikronContext;
import net.reevik.mikron.test2.ManagedConfiguration;
import org.junit.jupiter.api.Test;

@ManagedApplication(packages = {"net.reevik.mikron.test2.*"})
public class ConfigurationBindingTest {

  @Test
  void testBindingOfPrimitiveTypes() {
    var context = MikronContext.init(ConfigurationBindingTest.class);
    Optional<ManagedConfiguration> instance = context.getInstance(
        ManagedConfiguration.class.getSimpleName());
    assertThat(instance).isPresent();
    var config = instance.map(ManagedConfiguration::getStrConfig).orElseThrow(AssertionError::new);
    assertThat(config).isEqualTo("test1");
  }

  @Test
  void testBindingOfCustomTypes() {
    var context = MikronContext.init(ConfigurationBindingTest.class);
    Optional<ManagedConfiguration> instance = context.getInstance(
        ManagedConfiguration.class.getSimpleName());
    assertThat(instance).isPresent();
    var config = instance.map(ManagedConfiguration::getEntity).orElseThrow(AssertionError::new);
    assertThat(config).isNotNull();
    assertThat(config.value()).isEqualTo("test2");
  }

  @Test
  void testNonExistingConfigurationMustBeNull() {
    var context = MikronContext.init(ConfigurationBindingTest.class);
    Optional<ManagedConfiguration> instance = context.getInstance(
        ManagedConfiguration.class.getSimpleName());
    assertThat(instance).isPresent();
    var config = instance.map(ManagedConfiguration::getNotExist).orElse(null);
    assertThat(config).isNull();
  }
}
