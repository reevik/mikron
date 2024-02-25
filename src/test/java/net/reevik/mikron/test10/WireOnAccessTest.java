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
package net.reevik.mikron.test10;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import net.reevik.mikron.annotation.ManagedApplication;
import net.reevik.mikron.annotation.ManagedTest;
import net.reevik.mikron.annotation.Wire;
import org.junit.jupiter.api.Test;

@ManagedApplication(packages = "net.reevik.mikron.test10.*")
@ManagedTest
public class WireOnAccessTest {

  @Wire
  private ManagedInstance managedInstance;

  @Test
  void dynamicWiringByName() {
    assertThat(managedInstance).isNotNull();
    assertThat(managedInstance.getDynamicManagedDependency()).isNotNull();
    IDynamicManagedDependency dynamicManagedDependency = managedInstance.getDynamicManagedDependency();
    assertThat(dynamicManagedDependency).isInstanceOf(Proxy.class);
    IDynamicManagedDependency instance1 = dynamicManagedDependency.execute();
    IDynamicManagedDependency instance2 = dynamicManagedDependency.execute();
    assertThat(instance1).isNotEqualTo(instance2);
    assertThat(managedInstance.getStaticBoundDynamicManagedDependencyByName()).isNotNull();
    assertThat(managedInstance.getStaticBoundDynamicManagedDependencyByName()).isNotInstanceOf(Proxy.class);
    assertThat(managedInstance.getStaticBoundDynamicManagedDependency()).isNotNull();
    assertThat(managedInstance.getStaticBoundDynamicManagedDependency()).isNotInstanceOf(Proxy.class);
  }
}
