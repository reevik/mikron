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
package net.reevik.mikron;

import java.io.IOException;
import java.util.Optional;

public class DependencyScanImpl {

  public static final String DEFAULT_BASE_PKG = "/";

  public void scan(String packageName) {
    String baseDir = Optional.ofNullable(packageName)
        .map(p -> p.replace(".", "/"))
        .orElse(DEFAULT_BASE_PKG);
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      classLoader.getResources(baseDir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
