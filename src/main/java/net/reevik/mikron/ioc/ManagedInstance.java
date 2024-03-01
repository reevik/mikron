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
package net.reevik.mikron.ioc;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import net.reevik.mikron.annotation.Configurable;
import net.reevik.mikron.annotation.Wire;
import net.reevik.mikron.configuration.ConfigurationBinding;
import net.reevik.mikron.string.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagedInstance {
  private final static Logger LOG = LoggerFactory.getLogger(ManagedInstance.class);
  private final Object instance;
  private final String managedInstanceName;
  private final MikronContext context;

  public ManagedInstance(Object instance,
      String managedInstanceName, MikronContext context) {
    this.instance = instance;
    this.managedInstanceName = managedInstanceName;
    this.context = context;
  }

  public void wire() {
    Arrays.stream(instance.getClass().getDeclaredFields())
        .filter(field -> field.isAnnotationPresent(Wire.class))
        .map(field -> new FieldInjectionPoint(instance, field, context))
        .forEach(FieldInjectionPoint::inject);
  }

  public void configSetup() {
    configSetup(managedInstanceName);
  }

  public void configSetup(String configurationSourceKey) {
    Arrays.stream(instance.getClass().getDeclaredFields())
        .filter(field -> field.isAnnotationPresent(Configurable.class))
        .forEach(field -> wireConfiguration(field, configurationSourceKey));
  }

  private void wireConfiguration(final Field field, String configurationSourceKey) {
    bindConfig(field, getConfigNameOnField(field), configurationSourceKey);
  }

  private void bindConfig(Field field, String configName, String configurationSourceKey) {
    try {
      if (field.trySetAccessible()) {
        var managedConfig = context.getConfiguration(configurationSourceKey);
        var converter = field.getAnnotation(Configurable.class).converter();
        var bindingInstance = context.getConverter(field.getType(), converter);
        var targetVal = bindingInstance.convert(
            managedConfig.map(g -> g.get(configName)).orElse(null));
        new ConfigurationBinding().bind(field, instance, targetVal);
      }
    } catch (IllegalAccessException e) {
      LOG.error("Cannot wire the field={} Reason={}", configName, e.getMessage());
    } catch (InvocationTargetException | NoSuchMethodException | InstantiationException e) {
      throw new RuntimeException(e);
    }
  }

  private String getConfigNameOnField(Field field) {
    var annotation = field.getAnnotation(Configurable.class);
    var name = annotation.name();
    return Str.isEmpty(name) ? field.getType().getName() : name;
  }

  public Object getInstance() {
    return instance;
  }

  public String getManagedInstanceName() {
    return managedInstanceName;
  }
}
