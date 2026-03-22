/*
 * Copyright 2026 Karellen, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.karellen.jdtls.kotlin.search;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class for exclusion from JaCoCo code coverage reports.
 * JaCoCo filters out classes and methods annotated with any annotation
 * whose simple name contains "Generated" (with {@code RUNTIME} or
 * {@code CLASS} retention).
 *
 * <p>Use this annotation on JDT interface stub methods that are required
 * by the interface contract but have no meaningful implementation to test
 * (e.g., read-only exceptions, unsupported operations, trivial getters
 * mandated by an interface).
 *
 * @author Arcadiy Ivanov
 */
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR })
public @interface CoverageExcludeGenerated {
}
