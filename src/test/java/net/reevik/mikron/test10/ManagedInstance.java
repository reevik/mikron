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

import net.reevik.mikron.annotation.Dynamic;
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.Wire;

@Managed
public class ManagedInstance {

  @Dynamic
  @Wire(name = "ManagedDependency")
  private IDynamicManagedDependency dynamicManagedDependency;

  @Wire(name = "ManagedDependency")
  private IDynamicManagedDependency staticBoundDynamicManagedDependencyByName;

  @Wire
  private IDynamicManagedDependency staticBoundDynamicManagedDependency;

  public IDynamicManagedDependency getDynamicManagedDependency() {
    return dynamicManagedDependency;
  }

  public IDynamicManagedDependency getStaticBoundDynamicManagedDependencyByName() {
    return staticBoundDynamicManagedDependencyByName;
  }

  public IDynamicManagedDependency getStaticBoundDynamicManagedDependency() {
    return staticBoundDynamicManagedDependency;
  }
}
