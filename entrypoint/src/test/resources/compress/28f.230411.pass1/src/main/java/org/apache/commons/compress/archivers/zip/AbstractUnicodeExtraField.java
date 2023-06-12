/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.commons.compress.archivers.zip;

import java.io.UnsupportedEncodingException;
import java.util.zip.CRC32;
import java.util.zip.ZipException;
import org.apache.commons.compress.utils.CharsetNames;

/**
 * A common base class for Unicode extra information extra fields.
 * @NotThreadSafe
 */
public abstract class AbstractUnicodeExtraField implements ZipExtraField {

    private long nameCRC32;

    private byte[] unicodeName;

    private byte[] data;

    protected AbstractUnicodeExtraField() {
        throw new AssertionError("This method should not be reached! Signature: AbstractUnicodeExtraField()");
    }

    /**
     * Assemble as unicode extension from the name/comment and
     * encoding of the original zip entry.
     *
     * @param text The file name or comment.
     * @param bytes The encoded of the filename or comment in the zip
     * file.
     * @param off The offset of the encoded filename or comment in
     * <code>bytes</code>.
     * @param len The length of the encoded filename or commentin
     * <code>bytes</code>.
     */
    protected AbstractUnicodeExtraField(String text, byte[] bytes, int off, int len) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes, off, len);
        nameCRC32 = crc32.getValue();
        try {
            unicodeName = text.getBytes(CharsetNames.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("FATAL: UTF-8 encoding not supported.", e);
        }
    }

    /**
     * Assemble as unicode extension from the name/comment and
     * encoding of the original zip entry.
     *
     * @param text The file name or comment.
     * @param bytes The encoded of the filename or comment in the zip
     * file.
     */
    protected AbstractUnicodeExtraField(String text, byte[] bytes) {
        this(text, bytes, 0, bytes.length);
    }

    /**
     * @return The CRC32 checksum of the filename or comment as
     *         encoded in the central directory of the zip file.
     */
    public long getNameCRC32() {
        return nameCRC32;
    }

    /**
     * @return The UTF-8 encoded name.
     */
    public byte[] getUnicodeName() {
        byte[] b = null;
        if (unicodeName != null) {
            b = new byte[unicodeName.length];
            System.arraycopy(unicodeName, 0, b, 0, b.length);
        }
        return b;
    }

    public byte[] getCentralDirectoryData() {
        throw new AssertionError("This method should not be reached! Signature: getCentralDirectoryData()");
    }

    public ZipShort getCentralDirectoryLength() {
        throw new AssertionError("This method should not be reached! Signature: getCentralDirectoryLength()");
    }

    public byte[] getLocalFileDataData() {
        throw new AssertionError("This method should not be reached! Signature: getLocalFileDataData()");
    }

    public ZipShort getLocalFileDataLength() {
        throw new AssertionError("This method should not be reached! Signature: getLocalFileDataLength()");
    }

    public void parseFromLocalFileData(byte[] buffer, int offset, int length) throws ZipException {
        if (length < 5) {
            throw new ZipException("UniCode path extra data must have at least 5 bytes.");
        }
        int version = buffer[offset];
        if (version != 0x01) {
            throw new ZipException("Unsupported version [" + version + "] for UniCode path extra data.");
        }
        nameCRC32 = ZipLong.getValue(buffer, offset + 1);
        unicodeName = new byte[length - 5];
        System.arraycopy(buffer, offset + 5, unicodeName, 0, length - 5);
        data = null;
    }

    /**
     * Doesn't do anything special since this class always uses the
     * same data in central directory and local file data.
     */
    public void parseFromCentralDirectoryData(byte[] buffer, int offset, int length) throws ZipException {
        parseFromLocalFileData(buffer, offset, length);
    }
}
