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
package org.apache.commons.compress.archivers.dump;

import java.io.IOException;
import org.apache.commons.compress.archivers.zip.ZipEncoding;

/**
 * This class represents identifying information about a Dump archive volume.
 * It consists the archive's dump date, label, hostname, device name and possibly
 * last mount point plus the volume's volume id andfirst record number.
 *
 * For the corresponding C structure see the header of {@link DumpArchiveEntry}.
 */
public class DumpArchiveSummary {

    private long dumpDate;

    private long previousDumpDate;

    private int volume;

    private String label;

    private int level;

    private String filesys;

    private String devname;

    private String hostname;

    private int flags;

    private int firstrec;

    private int ntrec;

    DumpArchiveSummary(byte[] buffer, ZipEncoding encoding) throws IOException {
        dumpDate = 1000L * DumpArchiveUtil.convert32(buffer, 4);
        previousDumpDate = 1000L * DumpArchiveUtil.convert32(buffer, 8);
        volume = DumpArchiveUtil.convert32(buffer, 12);
        label = DumpArchiveUtil.decode(encoding, buffer, 676, DumpArchiveConstants.LBLSIZE).trim();
        level = DumpArchiveUtil.convert32(buffer, 692);
        filesys = DumpArchiveUtil.decode(encoding, buffer, 696, DumpArchiveConstants.NAMELEN).trim();
        devname = DumpArchiveUtil.decode(encoding, buffer, 760, DumpArchiveConstants.NAMELEN).trim();
        hostname = DumpArchiveUtil.decode(encoding, buffer, 824, DumpArchiveConstants.NAMELEN).trim();
        flags = DumpArchiveUtil.convert32(buffer, 888);
        firstrec = DumpArchiveUtil.convert32(buffer, 892);
        ntrec = DumpArchiveUtil.convert32(buffer, 896);
        //extAttributes = DumpArchiveUtil.convert32(buffer, 900);
    }

    /**
     * Get the device name, e.g., /dev/sda3 or /dev/mapper/vg0-home.
     * @return device name
     */
    public String getDevname() {
        return devname;
    }

    /**
     * Get the hostname of the system where the dump was performed.
     * @return hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Get the number of records per tape block. This is typically
     * between 10 and 32.
     * @return the number of records per tape block
     */
    public int getNTRec() {
        return ntrec;
    }

    /**
     * Is this volume compressed? N.B., individual blocks may or may not be compressed.
     * The first block is never compressed.
     * @return true if volume is compressed
     */
    public boolean isCompressed() {
        return (flags & 0x0080) == 0x0080;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int hash = 17;
        if (label != null) {
            hash = label.hashCode();
        }
        hash += 31 * dumpDate;
        if (hostname != null) {
            hash = (31 * hostname.hashCode()) + 17;
        }
        if (devname != null) {
            hash = (31 * devname.hashCode()) + 17;
        }
        return hash;
    }

    /**
     * @see java.lang.Object#equals(Object)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !o.getClass().equals(getClass())) {
            return false;
        }
        DumpArchiveSummary rhs = (DumpArchiveSummary) o;
        if (dumpDate != rhs.dumpDate) {
            return false;
        }
        if ((getHostname() == null) || !getHostname().equals(rhs.getHostname())) {
            return false;
        }
        if ((getDevname() == null) || !getDevname().equals(rhs.getDevname())) {
            return false;
        }
        return true;
    }
}
