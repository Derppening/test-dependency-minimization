/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.archivers;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Archive output stream implementations are expected to override the
 * {@link #write(byte[], int, int)} method to improve performance.
 * They should also override {@link #close()} to ensure that any necessary
 * trailers are added.
 *
 * <p>The normal sequence of calls when working with ArchiveOutputStreams is:</p>
 * <ul>
 *   <li>Create ArchiveOutputStream object,</li>
 *   <li>optionally write SFX header (Zip only),</li>
 *   <li>repeat as needed:
 *     <ul>
 *       <li>{@link #putArchiveEntry(ArchiveEntry)} (writes entry header),
 *       <li>{@link #write(byte[])} (writes entry data, as often as needed),
 *       <li>{@link #closeArchiveEntry()} (closes entry),
 *     </ul>
 *   </li>
 *   <li> {@link #finish()} (ends the addition of entries),</li>
 *   <li> optionally write additional data, provided format supports it,</li>
 *   <li>{@link #close()}.</li>
 * </ul>
 */
public abstract class ArchiveOutputStream extends OutputStream {

    /**
     * Temporary buffer used for the {@link #write(int)} method
     */
    private final byte[] oneByte = new byte[1];

    static final int BYTE_MASK = 0xFF;

    /**
     * holds the number of bytes written to this stream
     */
    private long bytesWritten = 0;

    // Methods specific to ArchiveOutputStream
    // Generic implementations of OutputStream methods that may be useful to sub-classes
    /**
     * Writes a byte to the current archive entry.
     *
     * <p>This method simply calls {@code write( byte[], 0, 1 )}.
     *
     * <p>MUST be overridden if the {@link #write(byte[], int, int)} method
     * is not overridden; may be overridden otherwise.
     *
     * @param b The byte to be written.
     * @throws IOException on error
     */
    @Override
    public void write(int b) throws IOException {
        oneByte[0] = (byte) (b & BYTE_MASK);
        write(oneByte, 0, 1);
    }

    /**
     * Increments the counter of already written bytes.
     * Doesn't increment if EOF has been hit ({@code written == -1}).
     *
     * @param written the number of bytes written
     */
    protected void count(int written) {
        count((long) written);
    }

    /**
     * Increments the counter of already written bytes.
     * Doesn't increment if EOF has been hit ({@code written == -1}).
     *
     * @param written the number of bytes written
     * @since 1.1
     */
    protected void count(long written) {
        if (written != -1) {
            bytesWritten = bytesWritten + written;
        }
    }

    /**
     * Returns the current number of bytes written to this stream.
     * @return the number of written bytes
     * @since 1.1
     */
    public long getBytesWritten() {
        return bytesWritten;
    }
}
