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
package net.reevik.mikron.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A managed instance, of which life cycle is under the control of the IoC microkernel. Other
 * managed instances which declare managed entities as a dependency by using {@link Wire} on their
 * class fields, may directly access them without explicitly instantiating them:
 *
 * <pre>
 *   &#064;Managed
 *   public class ManagedInstance {
 *
 *       &#064;Wire
 *       private AnotherManagedInstance another;
 *
 *       public void foo() {
 *            another.bar();
 *       }
 *   }
 * </pre>
 *
 * The manage instances can be explicitly named. In this case, optional managedInstanceName parameter is used in
 * {@link Managed} and {@link Wire} annotations:
 *
 * <pre>
 *   &#064;Managed(managedInstanceName="This")
 *   public class ManagedInstance {
 *
 *       &#064;Wire(managedInstanceName="That")
 *       private AnotherManagedInstance another;
 *
 *       public void foo() {
 *            another.bar();
 *       }
 *   }
 * </pre>
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Managed {

  /**
   * Optional managedInstanceName of the managed instance that can be used in {@link Wire} annotation.
   *
   * @return The managedInstanceName of the managed instance.
   */
  String name() default "";
}
