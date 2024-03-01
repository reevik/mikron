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
package net.reevik.mikron.test11;

import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.Prefer;
import net.reevik.mikron.annotation.Wire;

@Managed
public record ConstructorInjected1(ManagedDependency managedDependency) {

  @Prefer
  public ConstructorInjected1(@Wire ManagedDependency managedDependency) {
    this.managedDependency = managedDependency;
  }
}
