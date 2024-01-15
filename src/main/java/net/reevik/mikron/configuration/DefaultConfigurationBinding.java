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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class DefaultConfigurationBinding implements IConfigurationBinding {
  private final Map<Class<?>, Function<String, ?>> converters = new HashMap<>();

  public DefaultConfigurationBinding() {
    converters.put(Integer.class, Integer::parseInt);
    converters.put(int.class, Integer::parseInt);
    converters.put(Short.class, Short::parseShort);
    converters.put(long.class, Long::parseLong);
    converters.put(Long.class, Long::parseLong);
    converters.put(short.class, Short::parseShort);
    converters.put(Boolean.class, Boolean::parseBoolean);
    converters.put(boolean.class, Boolean::parseBoolean);
    converters.put(Float.class, Float::parseFloat);
    converters.put(float.class, Float::parseFloat);
    converters.put(Double.class, Double::parseDouble);
    converters.put(double.class, Double::parseDouble);
  }

  @Override
  public void bind(Field managedField, Object managedInstance, Object configValue) {
    try {
      managedField.setAccessible(true);
      if (managedField.getType().isAssignableFrom(configValue.getClass())) {
        managedField.set(managedInstance, configValue);
      } else {
        final var type = managedField.getType();
        if (type.isAssignableFrom(configValue.getClass())) {
          managedField.set(managedInstance, Integer.parseInt((String) configValue));
        } else {
          if (!converters.containsKey(type)) {
            throw new IllegalArgumentException(DefaultConfigurationBinding.class.getSimpleName() +
                " doesn't know how to convert '" + configValue + "' value into " + type);
          }
          Function<String, ?> stringFunction = converters.get(type);
          managedField.set(managedInstance, stringFunction.apply(configValue.toString()));
        }
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
