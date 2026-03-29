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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchParticipant;

/**
 * Search document for Kotlin files. Reads actual file content from the
 * workspace for ANTLR4-based parsing.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinSearchDocument extends SearchDocument {

	private char[] charContents;
	private byte[] byteContents;

	protected KotlinSearchDocument(String documentPath, SearchParticipant participant) {
		super(documentPath, participant);
	}

	@CoverageExcludeGenerated
	@Override
	public byte[] getByteContents() {
		if (byteContents == null) {
			loadContents();
		}
		return byteContents;
	}

	@Override
	public char[] getCharContents() {
		if (charContents == null) {
			loadContents();
		}
		return charContents;
	}

	@CoverageExcludeGenerated
	@Override
	public String getEncoding() {
		return "UTF-8";
	}

	private void loadContents() {
		String path = getPath();
		if (path == null) {
			byteContents = new byte[0];
			charContents = new char[0];
			return;
		}
		IFile file = ResourcesPlugin.getWorkspace().getRoot()
				.getFile(IPath.fromPortableString(path));
		if (file == null || !file.exists()) {
			byteContents = new byte[0];
			charContents = new char[0];
			return;
		}
		try (InputStream is = file.getContents(true)) {
			byteContents = is.readAllBytes();
			charContents = new String(byteContents, StandardCharsets.UTF_8).toCharArray();
		} catch (CoreException | IOException e) {
			Platform.getLog(
					KotlinSearchDocument.class).warn(
					"Failed to read search document: "
							+ getPath(), e);
			byteContents = new byte[0];
			charContents = new char[0];
		}
	}
}
