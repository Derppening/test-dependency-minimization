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

import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.compress.archivers.ArchiveEntry;

/**
 * This class represents an entry in a Dump archive. It consists
 * of the entry's header, the entry's File and any extended attributes.
 * <p>
 * DumpEntries that are created from the header bytes read from
 * an archive are instantiated with the DumpArchiveEntry( byte[] )
 * constructor. These entries will be used when extracting from
 * or listing the contents of an archive. These entries have their
 * header filled in using the header bytes. They also set the File
 * to null, since they reference an archive entry not a file.
 * <p>
 * DumpEntries can also be constructed from nothing but a name.
 * This allows the programmer to construct the entry by hand, for
 * instance when only an InputStream is available for writing to
 * the archive, and the header information is constructed from
 * other information. In this case the header fields are set to
 * defaults and the File is set to null.
 *
 * <p>
 * The C structure for a Dump Entry's header is:
 * <pre>
 * #define TP_BSIZE    1024          // size of each file block
 * #define NTREC       10            // number of blocks to write at once
 * #define HIGHDENSITYTREC 32        // number of blocks to write on high-density tapes
 * #define TP_NINDIR   (TP_BSIZE/2)  // number if indirect inodes in record
 * #define TP_NINOS    (TP_NINDIR / sizeof (int32_t))
 * #define LBLSIZE     16
 * #define NAMELEN     64
 *
 * #define OFS_MAGIC     (int)60011  // old format magic value
 * #define NFS_MAGIC     (int)60012  // new format magic value
 * #define FS_UFS2_MAGIC (int)0x19540119
 * #define CHECKSUM      (int)84446  // constant used in checksum algorithm
 *
 * struct  s_spcl {
 *   int32_t c_type;             // record type (see below)
 *   int32_t <b>c_date</b>;             // date of this dump
 *   int32_t <b>c_ddate</b>;            // date of previous dump
 *   int32_t c_volume;           // dump volume number
 *   u_int32_t c_tapea;          // logical block of this record
 *   dump_ino_t c_ino;           // number of inode
 *   int32_t <b>c_magic</b>;            // magic number (see above)
 *   int32_t c_checksum;         // record checksum
 * #ifdef  __linux__
 *   struct  new_bsd_inode c_dinode;
 * #else
 * #ifdef sunos
 *   struct  new_bsd_inode c_dinode;
 * #else
 *   struct  dinode  c_dinode;   // ownership and mode of inode
 * #endif
 * #endif
 *   int32_t c_count;            // number of valid c_addr entries
 *   union u_data c_data;        // see above
 *   char    <b>c_label[LBLSIZE]</b>;   // dump label
 *   int32_t <b>c_level</b>;            // level of this dump
 *   char    <b>c_filesys[NAMELEN]</b>; // name of dumpped file system
 *   char    <b>c_dev[NAMELEN]</b>;     // name of dumpped device
 *   char    <b>c_host[NAMELEN]</b>;    // name of dumpped host
 *   int32_t c_flags;            // additional information (see below)
 *   int32_t c_firstrec;         // first record on volume
 *   int32_t c_ntrec;            // blocksize on volume
 *   int32_t c_extattributes;    // additional inode info (see below)
 *   int32_t c_spare[30];        // reserved for future uses
 * } s_spcl;
 *
 * //
 * // flag values
 * //
 * #define DR_NEWHEADER     0x0001  // new format tape header
 * #define DR_NEWINODEFMT   0x0002  // new format inodes on tape
 * #define DR_COMPRESSED    0x0080  // dump tape is compressed
 * #define DR_METAONLY      0x0100  // only the metadata of the inode has been dumped
 * #define DR_INODEINFO     0x0002  // [SIC] TS_END header contains c_inos information
 * #define DR_EXTATTRIBUTES 0x8000
 *
 * //
 * // extattributes inode info
 * //
 * #define EXT_REGULAR         0
 * #define EXT_MACOSFNDRINFO   1
 * #define EXT_MACOSRESFORK    2
 * #define EXT_XATTR           3
 *
 * // used for EA on tape
 * #define EXT2_GOOD_OLD_INODE_SIZE    128
 * #define EXT2_XATTR_MAGIC        0xEA020000  // block EA
 * #define EXT2_XATTR_MAGIC2       0xEA020001  // in inode EA
 * </pre>
 * <p>
 * The fields in <b>bold</b> are the same for all blocks. (This permitted
 * multiple dumps to be written to a single tape.)
 * </p>
 *
 * <p>
 * The C structure for the inode (file) information is:
 * <pre>
 * struct bsdtimeval {           //  **** alpha-*-linux is deviant
 *   __u32   tv_sec;
 *   __u32   tv_usec;
 * };
 *
 * #define NDADDR      12
 * #define NIADDR       3
 *
 * //
 * // This is the new (4.4) BSD inode structure
 * // copied from the FreeBSD 2.0 &lt;ufs/ufs/dinode.h&gt; include file
 * //
 * struct new_bsd_inode {
 *   __u16       di_mode;           // file type, standard Unix permissions
 *   __s16       di_nlink;          // number of hard links to file.
 *   union {
 *      __u16       oldids[2];
 *      __u32       inumber;
 *   }           di_u;
 *   u_quad_t    di_size;           // file size
 *   struct bsdtimeval   di_atime;  // time file was last accessed
 *   struct bsdtimeval   di_mtime;  // time file was last modified
 *   struct bsdtimeval   di_ctime;  // time file was created
 *   __u32       di_db[NDADDR];
 *   __u32       di_ib[NIADDR];
 *   __u32       di_flags;          //
 *   __s32       di_blocks;         // number of disk blocks
 *   __s32       di_gen;            // generation number
 *   __u32       di_uid;            // user id (see /etc/passwd)
 *   __u32       di_gid;            // group id (see /etc/group)
 *   __s32       di_spare[2];       // unused
 * };
 * </pre>
 * <p>
 * It is important to note that the header DOES NOT have the name of the
 * file. It can't since hard links mean that you may have multiple filenames
 * for a single physical file. You must read the contents of the directory
 * entries to learn the mapping(s) from filename to inode.
 * </p>
 *
 * <p>
 * The C structure that indicates if a specific block is a real block
 * that contains data or is a sparse block that is not persisted to the
 * disk is:</p>
 * <pre>
 * #define TP_BSIZE    1024
 * #define TP_NINDIR   (TP_BSIZE/2)
 *
 * union u_data {
 *   char    s_addrs[TP_NINDIR]; // 1 =&gt; data; 0 =&gt; hole in inode
 *   int32_t s_inos[TP_NINOS];   // table of first inode on each volume
 * } u_data;
 * </pre>
 *
 * @NotThreadSafe
 */
public class DumpArchiveEntry implements ArchiveEntry {

    private String name;

    private TYPE type = TYPE.UNKNOWN;

    private int mode;

    private Set<PERMISSION> permissions = Collections.emptySet();

    private long size;

    private long atime;

    private long mtime;

    private int uid;

    private int gid;

    /**
     * Currently unused
     */
    private final DumpArchiveSummary summary = null;

    // this information is available from standard index.
    private final TapeSegmentHeader header = new TapeSegmentHeader();

    private String simpleName;

    private String originalName;

    // this information is available from QFA index
    private int volume;

    private long offset;

    private int ino;

    private int nlink;

    private long ctime;

    private int generation;

    /**
     * Default constructor.
     */
    public DumpArchiveEntry() {
    }

    /**
     * Constructor taking only filename.
     * @param name pathname
     * @param simpleName actual filename.
     */
    public DumpArchiveEntry(String name, String simpleName) {
        throw new AssertionError("This method should not be reached! Signature: DumpArchiveEntry(String, String)");
    }

    /**
     * Constructor taking name, inode and type.
     *
     * @param name
     * @param simpleName
     * @param ino
     * @param type
     */
    protected DumpArchiveEntry(String name, String simpleName, int ino, TYPE type) {
        throw new AssertionError("This method should not be reached! Signature: DumpArchiveEntry(String, String, int, TYPE)");
    }

    /**
     * Constructor taking tape buffer.
     * @param buffer
     * @param offset
     */
    /**
     * Sets the path of the entry.
     */
    protected void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    /**
     * Returns the ino of the entry.
     */
    public int getIno() {
        return header.getIno();
    }

    /**
     * Set the offset within the archive.
     */
    public void setOffset(long offset) {
        this.offset = offset;
    }

    /**
     * Return the type of the tape segment header.
     */
    public DumpArchiveConstants.SEGMENT_TYPE getHeaderType() {
        return header.getType();
    }

    /**
     * Return the number of records in this segment.
     */
    public int getHeaderCount() {
        return header.getCount();
    }

    /**
     * Return the number of sparse records in this segment.
     */
    public int getHeaderHoles() {
        return header.getHoles();
    }

    /**
     * Is this a sparse record?
     */
    public boolean isSparseRecord(int idx) {
        return (header.getCdata(idx) & 0x01) == 0;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return ino;
    }

    /**
     * @see java.lang.Object#equals(Object o)
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !o.getClass().equals(getClass())) {
            return false;
        }
        DumpArchiveEntry rhs = (DumpArchiveEntry) o;
        if ((header == null) || (rhs.header == null)) {
            return false;
        }
        if (ino != rhs.ino) {
            return false;
        }
        if ((summary == null && rhs.summary != null) || (summary != null && !summary.equals(rhs.summary))) {
            return false;
        }
        return true;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Populate the dump archive entry and tape segment header with
     * the contents of the buffer.
     *
     * @param buffer
     * @throws Exception
     */
    static DumpArchiveEntry parse(byte[] buffer) {
        DumpArchiveEntry entry = new DumpArchiveEntry();
        TapeSegmentHeader header = entry.header;
        header.type = DumpArchiveConstants.SEGMENT_TYPE.find(DumpArchiveUtil.convert32(buffer, 0));
        //header.dumpDate = new Date(1000L * DumpArchiveUtil.convert32(buffer, 4));
        //header.previousDumpDate = new Date(1000L * DumpArchiveUtil.convert32(
        //            buffer, 8));
        header.volume = DumpArchiveUtil.convert32(buffer, 12);
        //header.tapea = DumpArchiveUtil.convert32(buffer, 16);
        entry.ino = header.ino = DumpArchiveUtil.convert32(buffer, 20);
        //header.magic = DumpArchiveUtil.convert32(buffer, 24);
        //header.checksum = DumpArchiveUtil.convert32(buffer, 28);
        int m = DumpArchiveUtil.convert16(buffer, 32);
        // determine the type of the file.
        entry.setType(TYPE.find((m >> 12) & 0x0F));
        // determine the standard permissions
        entry.setMode(m);
        entry.nlink = DumpArchiveUtil.convert16(buffer, 34);
        // inumber, oldids?
        entry.setSize(DumpArchiveUtil.convert64(buffer, 40));
        long t = (1000L * DumpArchiveUtil.convert32(buffer, 48)) + (DumpArchiveUtil.convert32(buffer, 52) / 1000);
        entry.setAccessTime(new Date(t));
        t = (1000L * DumpArchiveUtil.convert32(buffer, 56)) + (DumpArchiveUtil.convert32(buffer, 60) / 1000);
        entry.setLastModifiedDate(new Date(t));
        t = (1000L * DumpArchiveUtil.convert32(buffer, 64)) + (DumpArchiveUtil.convert32(buffer, 68) / 1000);
        entry.ctime = t;
        // db: 72-119 - direct blocks
        // id: 120-131 - indirect blocks
        //entry.flags = DumpArchiveUtil.convert32(buffer, 132);
        //entry.blocks = DumpArchiveUtil.convert32(buffer, 136);
        entry.generation = DumpArchiveUtil.convert32(buffer, 140);
        entry.setUserId(DumpArchiveUtil.convert32(buffer, 144));
        entry.setGroupId(DumpArchiveUtil.convert32(buffer, 148));
        // two 32-bit spare values.
        header.count = DumpArchiveUtil.convert32(buffer, 160);
        header.holes = 0;
        for (int i = 0; (i < 512) && (i < header.count); i++) {
            if (buffer[164 + i] == 0) {
                header.holes++;
            }
        }
        System.arraycopy(buffer, 164, header.cdata, 0, 512);
        entry.volume = header.getVolume();
        //entry.isSummaryOnly = false;
        return entry;
    }

    /**
     * Archive entry as stored on tape. There is one TSH for (at most)
     * every 512k in the file.
     */
    static class TapeSegmentHeader {

        private DumpArchiveConstants.SEGMENT_TYPE type;

        private int volume;

        private int ino;

        private int count;

        private int holes;

        // map of any 'holes'
        private final byte[] cdata = new byte[512];

        public DumpArchiveConstants.SEGMENT_TYPE getType() {
            return type;
        }

        public int getVolume() {
            return volume;
        }

        public int getIno() {
            return ino;
        }

        public int getCount() {
            return count;
        }

        public int getHoles() {
            return holes;
        }

        public int getCdata(int idx) {
            return cdata[idx];
        }
    }

    /**
     * Returns the name of the entry.
     * @return the name of the entry.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the unmodified name of the entry.
     * @return the name of the entry.
     */
    String getOriginalName() {
        return originalName;
    }

    /**
     * Sets the name of the entry.
     */
    public final void setName(String name) {
        this.originalName = name;
        if (name != null) {
            if (isDirectory() && !name.endsWith("/")) {
                name += "/";
            }
            if (name.startsWith("./")) {
                name = name.substring(2);
            }
        }
        this.name = name;
    }

    /**
     * Is this a directory?
     */
    public boolean isDirectory() {
        return type == TYPE.DIRECTORY;
    }

    /**
     * Set the type of the entry.
     */
    public void setType(TYPE type) {
        this.type = type;
    }

    /**
     * Set the access permissions on the entry.
     */
    public void setMode(int mode) {
        this.mode = mode & 07777;
        this.permissions = PERMISSION.find(mode);
    }

    /**
     * Returns the size of the entry.
     */
    public long getSize() {
        return isDirectory() ? SIZE_UNKNOWN : size;
    }

    /**
     * Returns the size of the entry as read from the archive.
     */
    long getEntrySize() {
        return size;
    }

    /**
     * Set the size of the entry.
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Set the time the file was last modified.
     */
    public void setLastModifiedDate(Date mtime) {
        this.mtime = mtime.getTime();
    }

    /**
     * Set the time the file was last accessed.
     */
    public void setAccessTime(Date atime) {
        this.atime = atime.getTime();
    }

    /**
     * Set the user id.
     */
    public void setUserId(int uid) {
        this.uid = uid;
    }

    /**
     * Set the group id.
     */
    public void setGroupId(int gid) {
        this.gid = gid;
    }

    public enum TYPE {

        DIRECTORY(4), UNKNOWN(15);

        private int code;

        private TYPE(int code) {
            this.code = code;
        }

        public static TYPE find(int code) {
            TYPE type = UNKNOWN;
            for (TYPE t : TYPE.values()) {
                if (code == t.code) {
                    type = t;
                }
            }
            return type;
        }
    }

    public enum PERMISSION {
        ;

        private int code;

        private PERMISSION(int code) {
            throw new AssertionError("This method should not be reached! Signature: PERMISSION(int)");
        }

        public static Set<PERMISSION> find(int code) {
            Set<PERMISSION> set = new HashSet<PERMISSION>();
            for (PERMISSION p : PERMISSION.values()) {
                if ((code & p.code) == p.code) {
                    set.add(p);
                }
            }
            if (set.isEmpty()) {
                return Collections.emptySet();
            }
            return EnumSet.copyOf(set);
        }
    }
}
