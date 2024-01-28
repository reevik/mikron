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
package net.reevik.mikron.configuration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public class PropertiesRepository {

  private final Map<String, Properties> configurations = new HashMap<>();

  public PropertiesRepository() {
    loadAllProperties();
  }

  public Set<String> getPropertyClassNames() {
    return configurations.keySet();
  }

  public Optional<Properties> getConfiguration(String name) {
    return Optional.ofNullable(configurations.get(name));
  }

  public void loadAllProperties() {
    var classPathRootURL = PropertiesRepository.class.getResource("/");
    if (classPathRootURL != null) {
      var classPathRootFile = classPathRootURL.getFile();
      var root = new File(classPathRootFile);
      var children = root.listFiles();
      if (root.isDirectory() && children != null) {
        Arrays.stream(children).forEach(child -> loadProperties(child.getName()));
      }
    }
  }

  private void loadProperties(String configName) {
    Properties properties = new Properties();
    try {
      var resource = getClass().getClassLoader().getResource(configName);
      if (resource != null) {
        var file = new File(resource.getFile());
        if (file.exists() && file.isFile()) {
          var resourceAsStream = getClass().getResourceAsStream("/" + file.getName());
          properties.load(resourceAsStream);
          configurations.put(configName.replace(".properties", ""), properties);
        }
      }
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
