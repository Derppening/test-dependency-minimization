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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import static org.apache.commons.compress.archivers.zip.ZipConstants.DATA_DESCRIPTOR_MIN_VERSION;
import static org.apache.commons.compress.archivers.zip.ZipConstants.DWORD;
import static org.apache.commons.compress.archivers.zip.ZipConstants.INITIAL_VERSION;
import static org.apache.commons.compress.archivers.zip.ZipConstants.SHORT;
import static org.apache.commons.compress.archivers.zip.ZipConstants.WORD;
import static org.apache.commons.compress.archivers.zip.ZipConstants.ZIP64_MAGIC;
import static org.apache.commons.compress.archivers.zip.ZipConstants.ZIP64_MAGIC_SHORT;
import static org.apache.commons.compress.archivers.zip.ZipConstants.ZIP64_MIN_VERSION;

/**
 * Reimplementation of {@link java.util.zip.ZipOutputStream
 * java.util.zip.ZipOutputStream} that does handle the extended
 * functionality of this package, especially internal/external file
 * attributes and extra fields with different layouts for local file
 * data and central directory entries.
 *
 * <p>This class will try to use {@link java.io.RandomAccessFile
 * RandomAccessFile} when you know that the output is going to go to a
 * file.</p>
 *
 * <p>If RandomAccessFile cannot be used, this implementation will use
 * a Data Descriptor to store size and CRC information for {@link
 * #DEFLATED DEFLATED} entries, this means, you don't need to
 * calculate them yourself.  Unfortunately this is not possible for
 * the {@link #STORED STORED} method, here setting the CRC and
 * uncompressed size information is required before {@link
 * #putArchiveEntry(ArchiveEntry)} can be called.</p>
 *
 * <p>As of Apache Commons Compress 1.3 it transparently supports Zip64
 * extensions and thus individual entries and archives larger than 4
 * GB or with more than 65536 entries in most cases but explicit
 * control is provided via {@link #setUseZip64}.  If the stream can not
 * user RandomAccessFile and you try to write a ZipArchiveEntry of
 * unknown size then Zip64 extensions will be disabled by default.</p>
 *
 * @NotThreadSafe
 */
public class ZipArchiveOutputStream extends ArchiveOutputStream {

    static final int BUFFER_SIZE = 512;

    /**
     * indicates if this archive is finished. protected for use in Jar implementation
     */
    protected boolean finished = false;

    /* 
     * Apparently Deflater.setInput gets slowed down a lot on Sun JVMs
     * when it gets handed a really big buffer.  See
     * https://issues.apache.org/bugzilla/show_bug.cgi?id=45396
     *
     * Using a buffer size of 8 kB proved to be a good compromise
     */
    private static final int DEFLATER_BLOCK_SIZE = 8192;

    /**
     * Compression method for deflated entries.
     */
    public static final int DEFLATED = java.util.zip.ZipEntry.DEFLATED;

    /**
     * Default compression level for deflated entries.
     */
    public static final int DEFAULT_COMPRESSION = Deflater.DEFAULT_COMPRESSION;

    /**
     * Compression method for stored entries.
     */
    public static final int STORED = java.util.zip.ZipEntry.STORED;

    /**
     * default encoding for file names and comment.
     */
    static final String DEFAULT_ENCODING = ZipEncodingHelper.UTF8;

    /**
     * General purpose flag, which indicates that filenames are
     * written in UTF-8.
     * @deprecated use {@link GeneralPurposeBit#UFT8_NAMES_FLAG} instead
     */
    @Deprecated
    public static final int EFS_FLAG = GeneralPurposeBit.UFT8_NAMES_FLAG;

    private static final byte[] EMPTY = new byte[0];

    /**
     * Current entry.
     */
    private CurrentEntry entry;

    /**
     * The file comment.
     */
    private String comment = "";

    /**
     * Compression level for next entry.
     */
    private int level = DEFAULT_COMPRESSION;

    /**
     * Has the compression level changed when compared to the last
     * entry?
     */
    private boolean hasCompressionLevelChanged = false;

    /**
     * Default compression method for next entry.
     */
    private int method = java.util.zip.ZipEntry.DEFLATED;

    /**
     * List of ZipArchiveEntries written so far.
     */
    private final List<ZipArchiveEntry> entries = new LinkedList<ZipArchiveEntry>();

    /**
     * CRC instance to avoid parsing DEFLATED data twice.
     */
    private final CRC32 crc = new CRC32();

    /**
     * Count the bytes written to out.
     */
    private long written = 0;

    /**
     * Start of central directory.
     */
    private long cdOffset = 0;

    /**
     * Length of central directory.
     */
    private long cdLength = 0;

    /**
     * Helper, a 0 as ZipShort.
     */
    private static final byte[] ZERO = { 0, 0 };

    /**
     * Helper, a 0 as ZipLong.
     */
    private static final byte[] LZERO = { 0, 0, 0, 0 };

    /**
     * Holds the offsets of the LFH starts for each entry.
     */
    private final Map<ZipArchiveEntry, Long> offsets = new HashMap<ZipArchiveEntry, Long>();

    /**
     * The encoding to use for filenames and the file comment.
     *
     * <p>For a list of possible values see <a
     * href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html</a>.
     * Defaults to UTF-8.</p>
     */
    private String encoding = DEFAULT_ENCODING;

    /**
     * The zip encoding to use for filenames and the file comment.
     *
     * This field is of internal use and will be set in {@link
     * #setEncoding(String)}.
     */
    private ZipEncoding zipEncoding = ZipEncodingHelper.getZipEncoding(DEFAULT_ENCODING);

    /**
     * This Deflater object is used for output.
     */
    protected final Deflater def = new Deflater(level, true);

    /**
     * This buffer serves as a Deflater.
     */
    private final byte[] buf = new byte[BUFFER_SIZE];

    /**
     * Optional random access output.
     */
    private final RandomAccessFile raf;

    private final OutputStream out;

    /**
     * whether to use the general purpose bit flag when writing UTF-8
     * filenames or not.
     */
    private boolean useUTF8Flag = true;

    /**
     * Whether to encode non-encodable file names as UTF-8.
     */
    private boolean fallbackToUTF8 = false;

    /**
     * whether to create UnicodePathExtraField-s for each entry.
     */
    private UnicodeExtraFieldPolicy createUnicodeExtraFields = UnicodeExtraFieldPolicy.NEVER;

    /**
     * Whether anything inside this archive has used a ZIP64 feature.
     *
     * @since 1.3
     */
    private boolean hasUsedZip64 = false;

    private Zip64Mode zip64Mode = Zip64Mode.AsNeeded;

    /**
     * Creates a new ZIP OutputStream filtering the underlying stream.
     * @param out the outputstream to zip
     */
    public ZipArchiveOutputStream(OutputStream out) {
        this.out = null;
        this.raf = null;
        throw new AssertionError("This method should not be reached! Signature: ZipArchiveOutputStream(OutputStream)");
    }

    /**
     * Creates a new ZIP OutputStream writing to a File.  Will use
     * random access if possible.
     * @param file the file to zip to
     * @throws IOException on error
     */
    public ZipArchiveOutputStream(File file) throws IOException {
        out = null;
        raf = null;
        throw new AssertionError("This method should not be reached! Signature: ZipArchiveOutputStream(File)");
    }

    /**
     * {@inheritDoc}
     * @throws Zip64RequiredException if the archive's size exceeds 4
     * GByte or there are more than 65535 entries inside the archive
     * and {@link #setUseZip64} is {@link Zip64Mode#Never}.
     */
    public void finish() throws IOException {
        if (finished) {
            throw new IOException("This archive has already been finished");
        }
        if (entry != null) {
            throw new IOException("This archive contains unclosed entries.");
        }
        cdOffset = written;
        for (ZipArchiveEntry ze : entries) {
            writeCentralFileHeader(ze);
        }
        cdLength = written - cdOffset;
        writeZip64CentralDirectory();
        writeCentralDirectoryEnd();
        offsets.clear();
        entries.clear();
        def.end();
        finished = true;
    }

    /**
     * Writes bytes to ZIP entry.
     * @param b the byte array to write
     * @param offset the start position to write from
     * @param length the number of bytes to write
     * @throws IOException on error
     */
    @Override
    public void write(byte[] b, int offset, int length) throws IOException {
        if (entry == null) {
            throw new IllegalStateException("No current entry");
        }
        ZipUtil.checkRequestedFeatures(entry.entry);
        entry.hasWritten = true;
        if (entry.entry.getMethod() == DEFLATED) {
            writeDeflated(b, offset, length);
        } else {
            writeOut(b, offset, length);
            written += length;
        }
        crc.update(b, offset, length);
        count(length);
    }

    /**
     * write implementation for DEFLATED entries.
     */
    private void writeDeflated(byte[] b, int offset, int length) throws IOException {
        if (length > 0 && !def.finished()) {
            entry.bytesRead += length;
            if (length <= DEFLATER_BLOCK_SIZE) {
                def.setInput(b, offset, length);
                deflateUntilInputIsNeeded();
            } else {
                final int fullblocks = length / DEFLATER_BLOCK_SIZE;
                for (int i = 0; i < fullblocks; i++) {
                    def.setInput(b, offset + i * DEFLATER_BLOCK_SIZE, DEFLATER_BLOCK_SIZE);
                    deflateUntilInputIsNeeded();
                }
                final int done = fullblocks * DEFLATER_BLOCK_SIZE;
                if (done < length) {
                    def.setInput(b, offset + done, length - done);
                    deflateUntilInputIsNeeded();
                }
            }
        }
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with the stream.
     *
     * @exception  IOException  if an I/O error occurs.
     * @throws Zip64RequiredException if the archive's size exceeds 4
     * GByte or there are more than 65535 entries inside the archive
     * and {@link #setUseZip64} is {@link Zip64Mode#Never}.
     */
    @Override
    public void close() throws IOException {
        if (!finished) {
            finish();
        }
        destroy();
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be written out to the stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void flush() throws IOException {
        if (out != null) {
            out.flush();
        }
    }

    /*
     * Various ZIP constants
     */
    /**
     * local file header signature
     */
    static final byte[] LFH_SIG = ZipLong.LFH_SIG.getBytes();

    /**
     * data descriptor signature
     */
    static final byte[] DD_SIG = ZipLong.DD_SIG.getBytes();

    /**
     * central file header signature
     */
    static final byte[] CFH_SIG = ZipLong.CFH_SIG.getBytes();

    /**
     * end of central dir signature
     */
    static final byte[] EOCD_SIG = ZipLong.getBytes(0X06054B50L);

    /**
     * ZIP64 end of central dir signature
     */
    static final byte[] ZIP64_EOCD_SIG = ZipLong.getBytes(0X06064B50L);

    /**
     * ZIP64 end of central dir locator signature
     */
    static final byte[] ZIP64_EOCD_LOC_SIG = ZipLong.getBytes(0X07064B50L);

    /**
     * Writes next block of compressed data to the output stream.
     * @throws IOException on error
     */
    protected final void deflate() throws IOException {
        int len = def.deflate(buf, 0, buf.length);
        if (len > 0) {
            writeOut(buf, 0, len);
            written += len;
        }
    }

    /**
     * Writes the central file header entry.
     * @param ze the entry to write
     * @throws IOException on error
     * @throws Zip64RequiredException if the archive's size exceeds 4
     * GByte and {@link Zip64Mode #setUseZip64} is {@link
     * Zip64Mode#Never}.
     */
    protected void writeCentralFileHeader(ZipArchiveEntry ze) throws IOException {
        writeOut(CFH_SIG);
        written += WORD;
        final long lfhOffset = offsets.get(ze).longValue();
        final boolean needsZip64Extra = hasZip64Extra(ze) || ze.getCompressedSize() >= ZIP64_MAGIC || ze.getSize() >= ZIP64_MAGIC || lfhOffset >= ZIP64_MAGIC;
        if (needsZip64Extra && zip64Mode == Zip64Mode.Never) {
            // must be the offset that is too big, otherwise an
            // exception would have been throw in putArchiveEntry or
            // closeArchiveEntry
            throw new Zip64RequiredException(Zip64RequiredException.ARCHIVE_TOO_BIG_MESSAGE);
        }
        handleZip64Extra(ze, lfhOffset, needsZip64Extra);
        // version made by
        // CheckStyle:MagicNumber OFF
        writeOut(ZipShort.getBytes((ze.getPlatform() << 8) | (!hasUsedZip64 ? DATA_DESCRIPTOR_MIN_VERSION : ZIP64_MIN_VERSION)));
        written += SHORT;
        final int zipMethod = ze.getMethod();
        final boolean encodable = zipEncoding.canEncode(ze.getName());
        writeVersionNeededToExtractAndGeneralPurposeBits(zipMethod, !encodable && fallbackToUTF8, needsZip64Extra);
        written += WORD;
        // compression method
        writeOut(ZipShort.getBytes(zipMethod));
        written += SHORT;
        // last mod. time and date
        writeOut(ZipUtil.toDosTime(ze.getTime()));
        written += WORD;
        // CRC
        // compressed length
        // uncompressed length
        writeOut(ZipLong.getBytes(ze.getCrc()));
        if (ze.getCompressedSize() >= ZIP64_MAGIC || ze.getSize() >= ZIP64_MAGIC) {
            writeOut(ZipLong.ZIP64_MAGIC.getBytes());
            writeOut(ZipLong.ZIP64_MAGIC.getBytes());
        } else {
            writeOut(ZipLong.getBytes(ze.getCompressedSize()));
            writeOut(ZipLong.getBytes(ze.getSize()));
        }
        // CheckStyle:MagicNumber OFF
        written += 12;
        // CheckStyle:MagicNumber ON
        ByteBuffer name = getName(ze);
        writeOut(ZipShort.getBytes(name.limit()));
        written += SHORT;
        // extra field length
        byte[] extra = ze.getCentralDirectoryExtra();
        writeOut(ZipShort.getBytes(extra.length));
        written += SHORT;
        // file comment length
        String comm = ze.getComment();
        if (comm == null) {
            comm = "";
        }
        ByteBuffer commentB = getEntryEncoding(ze).encode(comm);
        writeOut(ZipShort.getBytes(commentB.limit()));
        written += SHORT;
        // disk number start
        writeOut(ZERO);
        written += SHORT;
        // internal file attributes
        writeOut(ZipShort.getBytes(ze.getInternalAttributes()));
        written += SHORT;
        // external file attributes
        writeOut(ZipLong.getBytes(ze.getExternalAttributes()));
        written += WORD;
        // relative offset of LFH
        writeOut(ZipLong.getBytes(Math.min(lfhOffset, ZIP64_MAGIC)));
        written += WORD;
        // file name
        writeOut(name.array(), name.arrayOffset(), name.limit() - name.position());
        written += name.limit();
        // extra field
        writeOut(extra);
        written += extra.length;
        // file comment
        writeOut(commentB.array(), commentB.arrayOffset(), commentB.limit() - commentB.position());
        written += commentB.limit();
    }

    /**
     * If the entry needs Zip64 extra information inside the central
     * directory then configure its data.
     */
    private void handleZip64Extra(ZipArchiveEntry ze, long lfhOffset, boolean needsZip64Extra) {
        if (needsZip64Extra) {
            Zip64ExtendedInformationExtraField z64 = getZip64Extra(ze);
            if (ze.getCompressedSize() >= ZIP64_MAGIC || ze.getSize() >= ZIP64_MAGIC) {
                z64.setCompressedSize(new ZipEightByteInteger(ze.getCompressedSize()));
                z64.setSize(new ZipEightByteInteger(ze.getSize()));
            } else {
                // reset value that may have been set for LFH
                z64.setCompressedSize(null);
                z64.setSize(null);
            }
            if (lfhOffset >= ZIP64_MAGIC) {
                z64.setRelativeHeaderOffset(new ZipEightByteInteger(lfhOffset));
            }
            ze.setExtra();
        }
    }

    /**
     * Writes the &quot;End of central dir record&quot;.
     * @throws IOException on error
     * @throws Zip64RequiredException if the archive's size exceeds 4
     * GByte or there are more than 65535 entries inside the archive
     * and {@link Zip64Mode #setUseZip64} is {@link Zip64Mode#Never}.
     */
    protected void writeCentralDirectoryEnd() throws IOException {
        writeOut(EOCD_SIG);
        // disk numbers
        writeOut(ZERO);
        writeOut(ZERO);
        // number of entries
        int numberOfEntries = entries.size();
        if (numberOfEntries > ZIP64_MAGIC_SHORT && zip64Mode == Zip64Mode.Never) {
            throw new Zip64RequiredException(Zip64RequiredException.TOO_MANY_ENTRIES_MESSAGE);
        }
        if (cdOffset > ZIP64_MAGIC && zip64Mode == Zip64Mode.Never) {
            throw new Zip64RequiredException(Zip64RequiredException.ARCHIVE_TOO_BIG_MESSAGE);
        }
        byte[] num = ZipShort.getBytes(Math.min(numberOfEntries, ZIP64_MAGIC_SHORT));
        writeOut(num);
        writeOut(num);
        // length and location of CD
        writeOut(ZipLong.getBytes(Math.min(cdLength, ZIP64_MAGIC)));
        writeOut(ZipLong.getBytes(Math.min(cdOffset, ZIP64_MAGIC)));
        // ZIP file comment
        ByteBuffer data = this.zipEncoding.encode(comment);
        writeOut(ZipShort.getBytes(data.limit()));
        writeOut(data.array(), data.arrayOffset(), data.limit() - data.position());
    }

    private static final byte[] ONE = ZipLong.getBytes(1L);

    /**
     * Writes the &quot;ZIP64 End of central dir record&quot; and
     * &quot;ZIP64 End of central dir locator&quot;.
     * @throws IOException on error
     * @since 1.3
     */
    protected void writeZip64CentralDirectory() throws IOException {
        if (zip64Mode == Zip64Mode.Never) {
            return;
        }
        if (!hasUsedZip64 && (cdOffset >= ZIP64_MAGIC || cdLength >= ZIP64_MAGIC || entries.size() >= ZIP64_MAGIC_SHORT)) {
            // actually "will use"
            hasUsedZip64 = true;
        }
        if (!hasUsedZip64) {
            return;
        }
        long offset = written;
        writeOut(ZIP64_EOCD_SIG);
        // size, we don't have any variable length as we don't support
        // the extensible data sector, yet
        writeOut(ZipEightByteInteger.getBytes(SHORT + /* version made by */
        SHORT + /* version needed to extract */
        WORD + /* disk number */
        WORD + /* disk with central directory */
        DWORD + /* number of entries in CD on this disk */
        DWORD + /* total number of entries */
        DWORD + /* size of CD */
        DWORD));
        // version made by and version needed to extract
        writeOut(ZipShort.getBytes(ZIP64_MIN_VERSION));
        writeOut(ZipShort.getBytes(ZIP64_MIN_VERSION));
        // disk numbers - four bytes this time
        writeOut(LZERO);
        writeOut(LZERO);
        // number of entries
        byte[] num = ZipEightByteInteger.getBytes(entries.size());
        writeOut(num);
        writeOut(num);
        // length and location of CD
        writeOut(ZipEightByteInteger.getBytes(cdLength));
        writeOut(ZipEightByteInteger.getBytes(cdOffset));
        // no "zip64 extensible data sector" for now
        // and now the "ZIP64 end of central directory locator"
        writeOut(ZIP64_EOCD_LOC_SIG);
        // disk number holding the ZIP64 EOCD record
        writeOut(LZERO);
        // relative offset of ZIP64 EOCD record
        writeOut(ZipEightByteInteger.getBytes(offset));
        // total number of disks
        writeOut(ONE);
    }

    /**
     * Write bytes to output or random access file.
     * @param data the byte array to write
     * @throws IOException on error
     */
    protected final void writeOut(byte[] data) throws IOException {
        writeOut(data, 0, data.length);
    }

    /**
     * Write bytes to output or random access file.
     * @param data the byte array to write
     * @param offset the start position to write from
     * @param length the number of bytes to write
     * @throws IOException on error
     */
    protected final void writeOut(byte[] data, int offset, int length) throws IOException {
        if (raf != null) {
            raf.write(data, offset, length);
        } else {
            out.write(data, offset, length);
        }
    }

    private void deflateUntilInputIsNeeded() throws IOException {
        while (!def.needsInput()) {
            deflate();
        }
    }

    private void writeVersionNeededToExtractAndGeneralPurposeBits(final int zipMethod, final boolean utfFallback, final boolean zip64) throws IOException {
        // CheckStyle:MagicNumber OFF
        int versionNeededToExtract = INITIAL_VERSION;
        GeneralPurposeBit b = new GeneralPurposeBit();
        b.useUTF8ForNames(useUTF8Flag || utfFallback);
        if (zipMethod == DEFLATED && raf == null) {
            // requires version 2 as we are going to store length info
            // in the data descriptor
            versionNeededToExtract = DATA_DESCRIPTOR_MIN_VERSION;
            b.useDataDescriptor(true);
        }
        if (zip64) {
            versionNeededToExtract = ZIP64_MIN_VERSION;
        }
        // CheckStyle:MagicNumber ON
        // version needed to extract
        writeOut(ZipShort.getBytes(versionNeededToExtract));
        // general purpose bit flag
        writeOut(b.encode());
    }

    /**
     * Get the existing ZIP64 extended information extra field or
     * create a new one and add it to the entry.
     *
     * @since 1.3
     */
    private Zip64ExtendedInformationExtraField getZip64Extra(ZipArchiveEntry ze) {
        if (entry != null) {
            entry.causedUseOfZip64 = !hasUsedZip64;
        }
        hasUsedZip64 = true;
        Zip64ExtendedInformationExtraField z64 = (Zip64ExtendedInformationExtraField) ze.getExtraField(Zip64ExtendedInformationExtraField.HEADER_ID);
        if (z64 == null) {
            /*
              System.err.println("Adding z64 for " + ze.getName()
              + ", method: " + ze.getMethod()
              + " (" + (ze.getMethod() == STORED) + ")"
              + ", raf: " + (raf != null));
            */
            z64 = new Zip64ExtendedInformationExtraField();
        }
        // even if the field is there already, make sure it is the first one
        ze.addAsFirstExtraField(z64);
        return z64;
    }

    /**
     * Is there a ZIP64 extended information extra field for the
     * entry?
     *
     * @since 1.3
     */
    private boolean hasZip64Extra(ZipArchiveEntry ze) {
        return ze.getExtraField(Zip64ExtendedInformationExtraField.HEADER_ID) != null;
    }

    private ZipEncoding getEntryEncoding(ZipArchiveEntry ze) {
        boolean encodable = zipEncoding.canEncode(ze.getName());
        return !encodable && fallbackToUTF8 ? ZipEncodingHelper.UTF8_ZIP_ENCODING : zipEncoding;
    }

    private ByteBuffer getName(ZipArchiveEntry ze) throws IOException {
        return getEntryEncoding(ze).encode(ze.getName());
    }

    /**
     * Closes the underlying stream/file without finishing the
     * archive, the result will likely be a corrupt archive.
     *
     * <p>This method only exists to support tests that generate
     * corrupt archives so they can clean up any temporary files.</p>
     */
    void destroy() throws IOException {
        if (raf != null) {
            raf.close();
        }
        if (out != null) {
            out.close();
        }
    }

    /**
     * enum that represents the possible policies for creating Unicode
     * extra fields.
     */
    public static final class UnicodeExtraFieldPolicy {

        /**
         * Always create Unicode extra fields.
         */
        public static final UnicodeExtraFieldPolicy ALWAYS = new UnicodeExtraFieldPolicy("always");

        /**
         * Never create Unicode extra fields.
         */
        public static final UnicodeExtraFieldPolicy NEVER = new UnicodeExtraFieldPolicy("never");

        /**
         * Create Unicode extra fields for filenames that cannot be
         * encoded using the specified encoding.
         */
        public static final UnicodeExtraFieldPolicy NOT_ENCODEABLE = new UnicodeExtraFieldPolicy("not encodeable");

        private final String name;

        private UnicodeExtraFieldPolicy(String n) {
            name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Structure collecting information for the entry that is
     * currently being written.
     */
    private static final class CurrentEntry {

        private CurrentEntry(ZipArchiveEntry entry) {
            this.entry = entry;
        }

        /**
         * Current ZIP entry.
         */
        private final ZipArchiveEntry entry;

        /**
         * Offset for CRC entry in the local file header data for the
         * current entry starts here.
         */
        private long localDataStart = 0;

        /**
         * Data for local header data
         */
        private long dataStart = 0;

        /**
         * Number of bytes read for the current entry (can't rely on
         * Deflater#getBytesRead) when using DEFLATED.
         */
        private long bytesRead = 0;

        /**
         * Whether current entry was the first one using ZIP64 features.
         */
        private boolean causedUseOfZip64 = false;

        /**
         * Whether write() has been called at all.
         *
         * <p>In order to create a valid archive {@link
         * #closeArchiveEntry closeArchiveEntry} will write an empty
         * array to get the CRC right if nothing has been written to
         * the stream at all.</p>
         */
        private boolean hasWritten;
    }
}
