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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class DynamicWiringInvocation implements InvocationHandler {

  private final Class<?> targetObjectType;

  private final MikronContext context;

  private final String componentName;

  private final String configurationSource;

  public DynamicWiringInvocation(Class<?> targetObjectType, MikronContext context,
      String componentNameOnField, String propClassName) {
    this.targetObjectType = targetObjectType;
    this.context = context;
    this.componentName = componentNameOnField;
    this.configurationSource = propClassName;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    var managedInstance = context.initObjectByAccess(targetObjectType, componentName);
    managedInstance.wire();
    managedInstance.configSetup(configurationSource);
    return method.invoke(managedInstance.getInstance(), args);
  }
}
