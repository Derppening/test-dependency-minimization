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
package org.apache.commons.compress.archivers.zip;

import java.util.zip.CRC32;
import java.util.zip.ZipException;

/**
 * Adds Unix file permission and UID/GID fields as well as symbolic
 * link handling.
 *
 * <p>This class uses the ASi extra field in the format:</p>
 * <pre>
 *         Value         Size            Description
 *         -----         ----            -----------
 * (Unix3) 0x756e        Short           tag for this extra block type
 *         TSize         Short           total data size for this block
 *         CRC           Long            CRC-32 of the remaining data
 *         Mode          Short           file permissions
 *         SizDev        Long            symlink'd size OR major/minor dev num
 *         UID           Short           user ID
 *         GID           Short           group ID
 *         (var.)        variable        symbolic link filename
 * </pre>
 * <p>taken from appnote.iz (Info-ZIP note, 981119) found at <a
 * href="ftp://ftp.uu.net/pub/archiving/zip/doc/">ftp://ftp.uu.net/pub/archiving/zip/doc/</a></p>
 *
 * <p>Short is two bytes and Long is four bytes in big endian byte and
 * word order, device numbers are currently not supported.</p>
 * @NotThreadSafe
 *
 * <p>Since the documentation this class is based upon doesn't mention
 * the character encoding of the file name at all, it is assumed that
 * it uses the current platform's default encoding.</p>
 */
public class AsiExtraField implements ZipExtraField, UnixStat, Cloneable {

    private static final ZipShort HEADER_ID = new ZipShort(0x756E);

    private static final int WORD = 4;

    /**
     * Standard Unix stat(2) file mode.
     */
    private int mode = 0;

    /**
     * User ID.
     */
    private int uid = 0;

    /**
     * Group ID.
     */
    private int gid = 0;

    /**
     * File this entry points to, if it is a symbolic link.
     *
     * <p>empty string - if entry is not a symbolic link.</p>
     */
    private String link = "";

    /**
     * Is this an entry for a directory?
     */
    private boolean dirFlag = false;

    /**
     * Instance used to calculate checksums.
     */
    private CRC32 crc = new CRC32();

    /**
     * Constructor for AsiExtraField.
     */
    public AsiExtraField() {
        throw new AssertionError("This method should not be reached! Signature: AsiExtraField()");
    }

    /**
     * The Header-ID.
     * @return the value for the header id for this extrafield
     */
    public ZipShort getHeaderId() {
        throw new AssertionError("This method should not be reached! Signature: getHeaderId()");
    }

    /**
     * Length of the extra field in the local file data - without
     * Header-ID or length specifier.
     * @return a <code>ZipShort</code> for the length of the data of this extra field
     */
    public ZipShort getLocalFileDataLength() {
        throw new AssertionError("This method should not be reached! Signature: getLocalFileDataLength()");
    }

    /**
     * Delegate to local file data.
     * @return the centralDirectory length
     */
    public ZipShort getCentralDirectoryLength() {
        throw new AssertionError("This method should not be reached! Signature: getCentralDirectoryLength()");
    }

    /**
     * The actual data to put into local file data - without Header-ID
     * or length specifier.
     * @return get the data
     */
    public byte[] getLocalFileDataData() {
        throw new AssertionError("This method should not be reached! Signature: getLocalFileDataData()");
    }

    /**
     * Delegate to local file data.
     * @return the local file data
     */
    public byte[] getCentralDirectoryData() {
        throw new AssertionError("This method should not be reached! Signature: getCentralDirectoryData()");
    }

    /**
     * Populate data from this array as if it was in local file data.
     * @param data an array of bytes
     * @param offset the start offset
     * @param length the number of bytes in the array from offset
     * @throws ZipException on error
     */
    public void parseFromLocalFileData(byte[] data, int offset, int length) throws ZipException {
        throw new AssertionError("This method should not be reached! Signature: parseFromLocalFileData(byte[], int, int)");
    }

    /**
     * Doesn't do anything special since this class always uses the
     * same data in central directory and local file data.
     */
    public void parseFromCentralDirectoryData(byte[] buffer, int offset, int length) throws ZipException {
        throw new AssertionError("This method should not be reached! Signature: parseFromCentralDirectoryData(byte[], int, int)");
    }

    @Override
    public Object clone() {
        try {
            AsiExtraField cloned = (AsiExtraField) super.clone();
            cloned.crc = new CRC32();
            return cloned;
        } catch (CloneNotSupportedException cnfe) {
            // impossible
            throw new RuntimeException(cnfe);
        }
    }
}
