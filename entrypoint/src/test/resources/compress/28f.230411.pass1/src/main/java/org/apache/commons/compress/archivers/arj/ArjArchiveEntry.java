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
package org.apache.commons.compress.archivers.arj;

import java.io.File;
import java.util.regex.Matcher;
import org.apache.commons.compress.archivers.ArchiveEntry;

/**
 * An entry in an ARJ archive.
 *
 * @NotThreadSafe
 * @since 1.6
 */
public class ArjArchiveEntry implements ArchiveEntry {

    private final LocalFileHeader localFileHeader;

    public ArjArchiveEntry() {
        localFileHeader = null;
        throw new AssertionError("This method should not be reached! Signature: ArjArchiveEntry()");
    }

    ArjArchiveEntry(final LocalFileHeader localFileHeader) {
        this.localFileHeader = localFileHeader;
    }

    /**
     * Get this entry's name.
     *
     * @return This entry's name.
     */
    public String getName() {
        if ((localFileHeader.arjFlags & LocalFileHeader.Flags.PATHSYM) != 0) {
            return localFileHeader.name.replaceAll("/", Matcher.quoteReplacement(File.separator));
        } else {
            return localFileHeader.name;
        }
    }

    /**
     * Get this entry's file size.
     *
     * @return This entry's file size.
     */
    public long getSize() {
        return localFileHeader.originalSize;
    }

    /**
     * True if the entry refers to a directory
     */
    public boolean isDirectory() {
        return localFileHeader.fileType == LocalFileHeader.FileTypes.DIRECTORY;
    }
}
