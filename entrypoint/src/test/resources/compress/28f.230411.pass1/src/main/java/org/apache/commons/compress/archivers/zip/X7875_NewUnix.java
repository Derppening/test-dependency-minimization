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

import java.io.Serializable;
import java.math.BigInteger;
import java.util.zip.ZipException;

/**
 * An extra field that stores UNIX UID/GID data (owner &amp; group ownership) for a given
 * zip entry.  We're using the field definition given in Info-Zip's source archive:
 * zip-3.0.tar.gz/proginfo/extrafld.txt
 *
 * <pre>
 * Value         Size        Description
 * -----         ----        -----------
 * 0x7875        Short       tag for this extra block type ("ux")
 * TSize         Short       total data size for this block
 * Version       1 byte      version of this extra field, currently 1
 * UIDSize       1 byte      Size of UID field
 * UID           Variable    UID for this entry (little endian)
 * GIDSize       1 byte      Size of GID field
 * GID           Variable    GID for this entry (little endian)
 * </pre>
 * @since 1.5
 */
public class X7875_NewUnix implements ZipExtraField, Cloneable, Serializable {

    private static final ZipShort HEADER_ID = new ZipShort(0x7875);

    private static final BigInteger ONE_THOUSAND = BigInteger.valueOf(1000);

    private static final long serialVersionUID = 1L;

    // always '1' according to current info-zip spec.
    private int version = 1;

    // BigInteger helps us with little-endian / big-endian conversions.
    // (thanks to BigInteger.toByteArray() and a reverse() method we created).
    // Also, the spec theoretically allows UID/GID up to 255 bytes long!
    //
    // NOTE:  equals() and hashCode() currently assume these can never be null.
    private BigInteger uid;

    private BigInteger gid;

    /**
     * Constructor for X7875_NewUnix.
     */
    public X7875_NewUnix() {
        throw new AssertionError("This method should not be reached! Signature: X7875_NewUnix()");
    }

    /**
     * The Header-ID.
     *
     * @return the value for the header id for this extrafield
     */
    public ZipShort getHeaderId() {
        throw new AssertionError("This method should not be reached! Signature: getHeaderId()");
    }

    /**
     * Length of the extra field in the local file data - without
     * Header-ID or length specifier.
     *
     * @return a <code>ZipShort</code> for the length of the data of this extra field
     */
    public ZipShort getLocalFileDataLength() {
        throw new AssertionError("This method should not be reached! Signature: getLocalFileDataLength()");
    }

    /**
     * Length of the extra field in the central directory data - without
     * Header-ID or length specifier.
     *
     * @return a <code>ZipShort</code> for the length of the data of this extra field
     */
    public ZipShort getCentralDirectoryLength() {
        throw new AssertionError("This method should not be reached! Signature: getCentralDirectoryLength()");
    }

    /**
     * The actual data to put into local file data - without Header-ID
     * or length specifier.
     *
     * @return get the data
     */
    public byte[] getLocalFileDataData() {
        throw new AssertionError("This method should not be reached! Signature: getLocalFileDataData()");
    }

    /**
     * The actual data to put into central directory data - without Header-ID
     * or length specifier.
     *
     * @return get the data
     */
    public byte[] getCentralDirectoryData() {
        throw new AssertionError("This method should not be reached! Signature: getCentralDirectoryData()");
    }

    /**
     * Populate data from this array as if it was in local file data.
     *
     * @param data   an array of bytes
     * @param offset the start offset
     * @param length the number of bytes in the array from offset
     * @throws java.util.zip.ZipException on error
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

    /**
     * Returns a String representation of this class useful for
     * debugging purposes.
     *
     * @return A String representation of this class useful for
     *         debugging purposes.
     */
    @Override
    public String toString() {
        return "0x7875 Zip Extra Field: UID=" + uid + " GID=" + gid;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof X7875_NewUnix) {
            X7875_NewUnix xf = (X7875_NewUnix) o;
            // We assume uid and gid can never be null.
            return version == xf.version && uid.equals(xf.uid) && gid.equals(xf.gid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hc = -1234567 * version;
        // Since most UID's and GID's are below 65,536, this is (hopefully!)
        // a nice way to make sure typical UID and GID values impact the hash
        // as much as possible.
        hc ^= Integer.rotateLeft(uid.hashCode(), 16);
        hc ^= gid.hashCode();
        return hc;
    }
}
