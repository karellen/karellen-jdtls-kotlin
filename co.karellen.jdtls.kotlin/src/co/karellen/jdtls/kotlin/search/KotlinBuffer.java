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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IBufferChangedListener;
import org.eclipse.jdt.core.IOpenable;

/**
 * Read-only {@link IBuffer} implementation for Kotlin source files.
 * Stores the file content as a string and provides character-level access
 * needed by jdtls handlers.
 *
 * @author Arcadiy Ivanov
 */
@CoverageExcludeGenerated
public class KotlinBuffer implements IBuffer {

	private final String contents;
	private final IOpenable owner;
	private volatile boolean closed;

	public KotlinBuffer(String contents, IOpenable owner) {
		this.contents = contents != null ? contents : "";
		this.owner = owner;
	}

	// ---- IBuffer implementation ----

	@Override
	public void addBufferChangedListener(IBufferChangedListener listener) {
		// Read-only buffer: no change tracking needed
	}

	@Override
	public void append(char[] text) {
		throw new UnsupportedOperationException("Kotlin buffer is read-only");
	}

	@Override
	public void append(String text) {
		throw new UnsupportedOperationException("Kotlin buffer is read-only");
	}

	@Override
	public void close() {
		closed = true;
	}

	@Override
	public char getChar(int position) {
		return contents.charAt(position);
	}

	@Override
	public char[] getCharacters() {
		return contents.toCharArray();
	}

	@Override
	public String getContents() {
		return contents;
	}

	@Override
	public int getLength() {
		return contents.length();
	}

	@Override
	public IOpenable getOwner() {
		return owner;
	}

	@Override
	public String getText(int offset, int length) {
		return contents.substring(offset, offset + length);
	}

	@Override
	public IResource getUnderlyingResource() {
		return null;
	}

	@Override
	public boolean hasUnsavedChanges() {
		return false;
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public void removeBufferChangedListener(IBufferChangedListener listener) {
		// No-op
	}

	@Override
	public void replace(int position, int length, char[] text) {
		throw new UnsupportedOperationException("Kotlin buffer is read-only");
	}

	@Override
	public void replace(int position, int length, String text) {
		throw new UnsupportedOperationException("Kotlin buffer is read-only");
	}

	@Override
	public void save(IProgressMonitor progress, boolean force) {
		// No-op: read-only buffer
	}

	@Override
	public void setContents(char[] characters) {
		throw new UnsupportedOperationException("Kotlin buffer is read-only");
	}

	@Override
	public void setContents(String contents) {
		throw new UnsupportedOperationException("Kotlin buffer is read-only");
	}
}
