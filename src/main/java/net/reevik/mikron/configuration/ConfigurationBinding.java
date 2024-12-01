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

import java.lang.reflect.Field;

/**
 * Injects the externalized configurations into the injection points.
 */
public class ConfigurationBinding implements IConfigurationBinding {

  @Override
  public void bind(Field managedField, Object managedInstance, Object configValue) {
    try {
      if (configValue != null) {
        managedField.setAccessible(true);
        if (managedField.getType().isAssignableFrom(configValue.getClass())) {
          managedField.set(managedInstance, configValue);
        } else {
          var type = managedField.getType();
          if (type.isAssignableFrom(configValue.getClass())) {
            managedField.set(managedInstance, Integer.parseInt((String) configValue));
          } else {
            managedField.set(managedInstance, configValue);
          }
        }
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
