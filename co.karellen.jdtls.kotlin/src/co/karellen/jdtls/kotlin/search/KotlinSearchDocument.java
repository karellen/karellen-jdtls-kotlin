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

import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchParticipant;

/**
 * Stub search document for Kotlin files. Does not read actual file content;
 * returns empty contents since the stub participant derives all index entries
 * from the file path.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinSearchDocument extends SearchDocument {

	protected KotlinSearchDocument(String documentPath, SearchParticipant participant) {
		super(documentPath, participant);
	}

	@Override
	public byte[] getByteContents() {
		return new byte[0];
	}

	@Override
	public char[] getCharContents() {
		return new char[0];
	}

	@Override
	public String getEncoding() {
		return "UTF-8";
	}
}
