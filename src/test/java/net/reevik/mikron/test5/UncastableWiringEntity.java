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
package net.reevik.mikron.test5;

import java.math.BigInteger;
import net.reevik.mikron.annotation.Managed;
import net.reevik.mikron.annotation.Wire;

@Managed(name="UncastableWiringEntity")
public class UncastableWiringEntity {

  @Wire(name="UncastableEntity")
  BigInteger targetEntity;

  public BigInteger getTargetEntity() {
    return targetEntity;
  }
}
