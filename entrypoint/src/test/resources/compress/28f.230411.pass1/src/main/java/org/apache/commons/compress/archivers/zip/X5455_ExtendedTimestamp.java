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
import java.util.Date;
import java.util.zip.ZipException;

/**
 * <p>An extra field that stores additional file and directory timestamp data
 * for zip entries.   Each zip entry can include up to three timestamps
 * (modify, access, create*).  The timestamps are stored as 32 bit unsigned
 * integers representing seconds since UNIX epoch (Jan 1st, 1970, UTC).
 * This field improves on zip's default timestamp granularity, since it
 * allows one to store additional timestamps, and, in addition, the timestamps
 * are stored using per-second granularity (zip's default behaviour can only store
 * timestamps to the nearest <em>even</em> second).
 * </p><p>
 * Unfortunately, 32 (unsigned) bits can only store dates up to the year 2106,
 * and so this extra field will eventually be obsolete.  Enjoy it while it lasts!
 * </p>
 * <ul>
 * <li><b>modifyTime:</b>
 * most recent time of file/directory modification
 * (or file/dir creation if the entry has not been
 * modified since it was created).
 * </li>
 * <li><b>accessTime:</b>
 * most recent time file/directory was opened
 * (e.g., read from disk).  Many people disable
 * their operating systems from updating this value
 * using the NOATIME mount option to optimize disk behaviour,
 * and thus it's not always reliable.  In those cases
 * it's always equal to modifyTime.
 * </li>
 * <li><b>*createTime:</b>
 * modern linux file systems (e.g., ext2 and newer)
 * do not appear to store a value like this, and so
 * it's usually omitted altogether in the zip extra
 * field.  Perhaps other unix systems track this.
 * </li></ul>
 * <p>
 * We're using the field definition given in Info-Zip's source archive:
 * zip-3.0.tar.gz/proginfo/extrafld.txt
 * </p>
 * <pre>
 * Value         Size        Description
 * -----         ----        -----------
 * 0x5455        Short       tag for this extra block type ("UT")
 * TSize         Short       total data size for this block
 * Flags         Byte        info bits
 * (ModTime)     Long        time of last modification (UTC/GMT)
 * (AcTime)      Long        time of last access (UTC/GMT)
 * (CrTime)      Long        time of original creation (UTC/GMT)
 *
 * Central-header version:
 *
 * Value         Size        Description
 * -----         ----        -----------
 * 0x5455        Short       tag for this extra block type ("UT")
 * TSize         Short       total data size for this block
 * Flags         Byte        info bits (refers to local header!)
 * (ModTime)     Long        time of last modification (UTC/GMT)
 * </pre>
 * @since 1.5
 */
public class X5455_ExtendedTimestamp implements ZipExtraField, Cloneable, Serializable {

    private static final ZipShort HEADER_ID = new ZipShort(0x5455);

    private static final long serialVersionUID = 1L;

    /**
     * The bit set inside the flags by when the last modification time
     * is present in this extra field.
     */
    public static final byte MODIFY_TIME_BIT = 1;

    /**
     * The bit set inside the flags by when the lasr access time is
     * present in this extra field.
     */
    public static final byte ACCESS_TIME_BIT = 2;

    /**
     * The bit set inside the flags by when the original creation time
     * is present in this extra field.
     */
    public static final byte CREATE_TIME_BIT = 4;

    // The 3 boolean fields (below) come from this flags byte.  The remaining 5 bits
    // are ignored according to the current version of the spec (December 2012).
    private byte flags;

    // Note: even if bit1 and bit2 are set, the Central data will still not contain
    // access/create fields:  only local data ever holds those!  This causes
    // some of our implementation to look a little odd, with seemingly spurious
    // != null and length checks.
    private boolean bit0_modifyTimePresent;

    private boolean bit1_accessTimePresent;

    private boolean bit2_createTimePresent;

    private ZipLong modifyTime;

    private ZipLong accessTime;

    private ZipLong createTime;

    /**
     * Constructor for X5455_ExtendedTimestamp.
     */
    public X5455_ExtendedTimestamp() {
        throw new AssertionError("This method should not be reached! Signature: X5455_ExtendedTimestamp()");
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
     * Length of the extra field in the local file data - without
     * Header-ID or length specifier.
     *
     * <p>For X5455 the central length is often smaller than the
     * local length, because central cannot contain access or create
     * timestamps.</p>
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
     * @return the central directory data
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
     * same parsing logic for both central directory and local file data.
     */
    public void parseFromCentralDirectoryData(byte[] buffer, int offset, int length) throws ZipException {
        throw new AssertionError("This method should not be reached! Signature: parseFromCentralDirectoryData(byte[], int, int)");
    }

    /**
     * Returns the modify time as a java.util.Date
     * of this zip entry, or null if no such timestamp exists in the zip entry.
     * The milliseconds are always zeroed out, since the underlying data
     * offers only per-second precision.
     *
     * @return modify time as java.util.Date or null.
     */
    public Date getModifyJavaTime() {
        return modifyTime != null ? new Date(modifyTime.getValue() * 1000) : null;
    }

    /**
     * Returns the access time as a java.util.Date
     * of this zip entry, or null if no such timestamp exists in the zip entry.
     * The milliseconds are always zeroed out, since the underlying data
     * offers only per-second precision.
     *
     * @return access time as java.util.Date or null.
     */
    public Date getAccessJavaTime() {
        return accessTime != null ? new Date(accessTime.getValue() * 1000) : null;
    }

    /**
     * <p>
     * Returns the create time as a a java.util.Date
     * of this zip entry, or null if no such timestamp exists in the zip entry.
     * The milliseconds are always zeroed out, since the underlying data
     * offers only per-second precision.
     * </p><p>
     * Note: modern linux file systems (e.g., ext2)
     * do not appear to store a "create time" value, and so
     * it's usually omitted altogether in the zip extra
     * field.  Perhaps other unix systems track this.
     *
     * @return create time as java.util.Date or null.
     */
    public Date getCreateJavaTime() {
        return createTime != null ? new Date(createTime.getValue() * 1000) : null;
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
        StringBuilder buf = new StringBuilder();
        buf.append("0x5455 Zip Extra Field: Flags=");
        buf.append(Integer.toBinaryString(ZipUtil.unsignedIntToSignedByte(flags))).append(" ");
        if (bit0_modifyTimePresent && modifyTime != null) {
            Date m = getModifyJavaTime();
            buf.append(" Modify:[").append(m).append("] ");
        }
        if (bit1_accessTimePresent && accessTime != null) {
            Date a = getAccessJavaTime();
            buf.append(" Access:[").append(a).append("] ");
        }
        if (bit2_createTimePresent && createTime != null) {
            Date c = getCreateJavaTime();
            buf.append(" Create:[").append(c).append("] ");
        }
        return buf.toString();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof X5455_ExtendedTimestamp) {
            X5455_ExtendedTimestamp xf = (X5455_ExtendedTimestamp) o;
            // The ZipLong==ZipLong clauses handle the cases where both are null.
            // and only last 3 bits of flags matter.
            return ((flags & 0x07) == (xf.flags & 0x07)) && (modifyTime == xf.modifyTime || (modifyTime != null && modifyTime.equals(xf.modifyTime))) && (accessTime == xf.accessTime || (accessTime != null && accessTime.equals(xf.accessTime))) && (createTime == xf.createTime || (createTime != null && createTime.equals(xf.createTime)));
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // only last 3 bits of flags matter
        int hc = (-123 * (flags & 0x07));
        if (modifyTime != null) {
            hc ^= modifyTime.hashCode();
        }
        if (accessTime != null) {
            // Since accessTime is often same as modifyTime,
            // this prevents them from XOR negating each other.
            hc ^= Integer.rotateLeft(accessTime.hashCode(), 11);
        }
        if (createTime != null) {
            hc ^= Integer.rotateLeft(createTime.hashCode(), 22);
        }
        return hc;
    }
}
