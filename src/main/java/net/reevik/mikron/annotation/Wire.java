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
 * The annotation, which is used to mark the dependency injection point on the class fields. The
 * managedInstanceName of the object can be provided as parameter, if the managed instance is a named one. You
 * can managedInstanceName the managed entities in their {@link Managed} annotation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Wire {

  /**
   * Optional managedInstanceName of the managed instance which will be injected.
   *
   * @return The managedInstanceName of the managed instance.
   */
  String name() default "";

  String filter() default "";

  Scope scope() default Scope.STATIC;
}
