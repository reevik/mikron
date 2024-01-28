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
package net.reevik.mikron.string;

/**
 * A string utility implementation. "Str" provides convenience methods to process char sequences.
 */
public class Str {

  private static final String BLANK = "";

  /**
   * Returns true if the input is null or doesn't contain any character other than spaces.
   * @param input String input.
   * @return If the {@link String} instance is empty.
   */
  public static boolean isEmpty(String input) {
    return input == null || BLANK.equals(input.trim());
  }

  /**
   * Returns true if the input is NOT null and contains any character other than spaces.
   * @param input String input.
   * @return If the {@link String} instance is not empty.
   */
  public static boolean isNotEmpty(String input) {
    return !isEmpty(input);
  }

  /**
   * Returns true if the input is NOT null and doesn't contain any character other than spaces.
   * @param input String input.
   * @return If the {@link String} instance blank.
   */
  public static boolean isBlank(String input) {
    return input != null && BLANK.equals(input.trim());
  }
}
