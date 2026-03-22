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

import java.util.ArrayList;
import java.util.List;

/**
 * Common interface for scope objects that span a source range.
 * Provides default {@code contains()} and static utility methods
 * for finding enclosing scopes at a given offset.
 *
 * @author Arcadiy Ivanov
 */
public interface ScopeRange {

	int getStartOffset();

	int getEndOffset();

	default boolean contains(int offset) {
		return offset >= getStartOffset() && offset <= getEndOffset();
	}

	/**
	 * Finds the innermost scope containing the given offset.
	 *
	 * @param <T>    the scope type
	 * @param scopes the scopes to search
	 * @param offset the source offset to check
	 * @return the innermost enclosing scope, or {@code null}
	 */
	static <T extends ScopeRange> T findEnclosing(List<T> scopes,
			int offset) {
		T best = null;
		for (T scope : scopes) {
			if (scope.contains(offset)) {
				if (best == null
						|| (scope.getEndOffset()
								- scope.getStartOffset())
								< (best.getEndOffset()
										- best.getStartOffset())) {
					best = scope;
				}
			}
		}
		return best;
	}

	/**
	 * Finds all scopes containing the given offset, sorted from
	 * outermost to innermost.
	 *
	 * @param <T>    the scope type
	 * @param scopes the scopes to search
	 * @param offset the source offset to check
	 * @return list of enclosing scopes outermost-first, empty if none
	 */
	static <T extends ScopeRange> List<T> findAllEnclosing(
			List<T> scopes, int offset) {
		List<T> result = new ArrayList<>();
		for (T scope : scopes) {
			if (scope.contains(offset)) {
				result.add(scope);
			}
		}
		result.sort((a, b) -> {
			int sizeA = a.getEndOffset() - a.getStartOffset();
			int sizeB = b.getEndOffset() - b.getStartOffset();
			return Integer.compare(sizeB, sizeA);
		});
		return result;
	}
}
