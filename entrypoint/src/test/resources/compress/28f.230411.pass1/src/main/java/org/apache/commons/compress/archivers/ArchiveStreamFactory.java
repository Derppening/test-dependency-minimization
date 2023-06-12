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

/**
 * Factory to create Archive[In|Out]putStreams from names or the first bytes of
 * the InputStream. In order to add other implementations, you should extend
 * ArchiveStreamFactory and override the appropriate methods (and call their
 * implementation from super of course).
 *
 * Compressing a ZIP-File:
 *
 * <pre>
 * final OutputStream out = new FileOutputStream(output);
 * ArchiveOutputStream os = new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.ZIP, out);
 *
 * os.putArchiveEntry(new ZipArchiveEntry("testdata/test1.xml"));
 * IOUtils.copy(new FileInputStream(file1), os);
 * os.closeArchiveEntry();
 *
 * os.putArchiveEntry(new ZipArchiveEntry("testdata/test2.xml"));
 * IOUtils.copy(new FileInputStream(file2), os);
 * os.closeArchiveEntry();
 * os.close();
 * </pre>
 *
 * Decompressing a ZIP-File:
 *
 * <pre>
 * final InputStream is = new FileInputStream(input);
 * ArchiveInputStream in = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP, is);
 * ZipArchiveEntry entry = (ZipArchiveEntry)in.getNextEntry();
 * OutputStream out = new FileOutputStream(new File(dir, entry.getName()));
 * IOUtils.copy(in, out);
 * out.close();
 * in.close();
 * </pre>
 *
 * @Immutable
 */
public class ArchiveStreamFactory {

    /**
     * Constant used to identify the AR archive format.
     * @since 1.1
     */
    public static final String AR = "ar";

    /**
     * Constant used to identify the ARJ archive format.
     * @since 1.6
     */
    public static final String ARJ = "arj";

    /**
     * Constant used to identify the CPIO archive format.
     * @since 1.1
     */
    public static final String CPIO = "cpio";

    /**
     * Constant used to identify the Unix DUMP archive format.
     * @since 1.3
     */
    public static final String DUMP = "dump";

    /**
     * Constant used to identify the JAR archive format.
     * @since 1.1
     */
    public static final String JAR = "jar";

    /**
     * Constant used to identify the TAR archive format.
     * @since 1.1
     */
    public static final String TAR = "tar";

    /**
     * Constant used to identify the ZIP archive format.
     * @since 1.1
     */
    public static final String ZIP = "zip";

    /**
     * Constant used to identify the 7z archive format.
     * @since 1.8
     */
    public static final String SEVEN_Z = "7z";

    /**
     * Entry encoding, null for the default.
     */
    private String entryEncoding = null;
}
