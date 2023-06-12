/*
 * Copyright 2009 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import com.google.common.base.Charsets;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * An abstract representation of a source file that provides access to
 * language-neutral features. The source file can be loaded from various
 * locations, such as from disk or from a preloaded string.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class SourceFile implements StaticSourceFile, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Number of lines in the region returned by {@link #getRegion(int)}.
     * This length must be odd.
     */
    private static final int SOURCE_EXCERPT_REGION_LENGTH = 5;

    private final String fileName;

    private boolean isExternFile = false;

    // The fileName may not always identify the original file - for example,
    // supersourced Java inputs, or Java inputs that come from Jar files. This
    // is an optional field that the creator of an AST or SourceFile can set.
    // It could be a path to the original file, or in case this SourceFile came
    // from a Jar, it could be the path to the Jar.
    private String originalPath = null;

    // Source Line Information
    private int[] lineOffsets = null;

    private String code = null;

    /**
     * Construct a new abstract source file.
     *
     * @param fileName The file name of the source file. It does not necessarily
     *     need to correspond to a real path. But it should be unique. Will
     *     appear in warning messages emitted by the compiler.
     */
    public SourceFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("a source must have a name");
        }
        this.fileName = fileName;
    }

    private void findLineOffsets() {
        if (lineOffsets != null) {
            return;
        }
        try {
            String[] sourceLines = getCode().split("\n");
            lineOffsets = new int[sourceLines.length];
            for (int ii = 1; ii < sourceLines.length; ++ii) {
                lineOffsets[ii] = lineOffsets[ii - 1] + sourceLines[ii - 1].length() + 1;
            }
        } catch (IOException e) {
            lineOffsets = new int[1];
            lineOffsets[0] = 0;
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    // Implementation
    /**
     * Gets all the code in this source file.
     * @throws IOException
     */
    public String getCode() throws IOException {
        return code;
    }

    private void setCode(String sourceCode) {
        code = sourceCode;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    // For SourceFile types which cache source code that can be regenerated
    // easily, flush the cache.  We maintain the cache mostly to speed up
    // generating source when displaying error messages, so dumping the file
    /**
     * Returns a unique name for the source file.
     */
    @Override
    public String getName() {
        return fileName;
    }

    /**
     * Returns whether this is an extern.
     */
    @Override
    public boolean isExtern() {
        return isExternFile;
    }

    /**
     * Sets that this is an extern.
     */
    void setIsExtern(boolean newVal) {
        isExternFile = newVal;
    }

    public int getLineOfOffset(int offset) {
        findLineOffsets();
        int search = Arrays.binarySearch(lineOffsets, offset);
        if (search >= 0) {
            // lines are 1-based.
            return search + 1;
        } else {
            int insertionPoint = -1 * (search + 1);
            return Math.min(insertionPoint - 1, lineOffsets.length - 1) + 1;
        }
    }

    public int getColumnOfOffset(int offset) {
        int line = getLineOfOffset(offset);
        return offset - lineOffsets[line - 1];
    }

    /**
     * Gets the source line for the indicated line number.
     *
     * @param lineNumber the line number, 1 being the first line of the file.
     * @return The line indicated. Does not include the newline at the end
     *     of the file. Returns {@code null} if it does not exist,
     *     or if there was an IO exception.
     */
    public String getLine(int lineNumber) {
        findLineOffsets();
        if (lineNumber > lineOffsets.length) {
            return null;
        }
        if (lineNumber < 1) {
            lineNumber = 1;
        }
        int pos = lineOffsets[lineNumber - 1];
        String js = "";
        try {
            // NOTE(nicksantos): Right now, this is optimized for few warnings.
            // This is probably the right trade-off, but will be slow if there
            // are lots of warnings in one file.
            js = getCode();
        } catch (IOException e) {
            return null;
        }
        if (js.indexOf('\n', pos) == -1) {
            // If next new line cannot be found, there are two cases
            // 1. pos already reaches the end of file, then null should be returned
            // 2. otherwise, return the contents between pos and the end of file.
            if (pos >= js.length()) {
                return null;
            } else {
                return js.substring(pos, js.length());
            }
        } else {
            return js.substring(pos, js.indexOf('\n', pos));
        }
    }

    /**
     * Get a region around the indicated line number. The exact definition of a
     * region is implementation specific, but it must contain the line indicated
     * by the line number. A region must not start or end by a carriage return.
     *
     * @param lineNumber the line number, 1 being the first line of the file.
     * @return The line indicated. Returns {@code null} if it does not exist,
     *     or if there was an IO exception.
     */
    public Region getRegion(int lineNumber) {
        String js = "";
        try {
            js = getCode();
        } catch (IOException e) {
            return null;
        }
        int pos = 0;
        int startLine = Math.max(1, lineNumber - (SOURCE_EXCERPT_REGION_LENGTH + 1) / 2 + 1);
        for (int n = 1; n < startLine; n++) {
            int nextpos = js.indexOf('\n', pos);
            if (nextpos == -1) {
                break;
            }
            pos = nextpos + 1;
        }
        int end = pos;
        int endLine = startLine;
        for (int n = 0; n < SOURCE_EXCERPT_REGION_LENGTH; n++, endLine++) {
            end = js.indexOf('\n', end);
            if (end == -1) {
                break;
            }
            end++;
        }
        if (lineNumber >= endLine) {
            return null;
        }
        if (end == -1) {
            int last = js.length() - 1;
            if (js.charAt(last) == '\n') {
                return new SimpleRegion(startLine, endLine, js.substring(pos, last));
            } else {
                return new SimpleRegion(startLine, endLine, js.substring(pos));
            }
        } else {
            return new SimpleRegion(startLine, endLine, js.substring(pos, end));
        }
    }

    @Override
    public String toString() {
        return fileName;
    }

    public static SourceFile fromCode(String fileName, String code) {
        return builder().buildFromCode(fileName, code);
    }

    /**
     * Create a new builder for source files.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder interface for source files.
     *
     * Allows users to customize the Charset, and the original path of
     * the source file (if it differs from the path on disk).
     */
    public static class Builder {

        private Charset charset = Charsets.UTF_8;

        private String originalPath = null;

        public Builder() {
        }

        public SourceFile buildFromCode(String fileName, String code) {
            return new Preloaded(fileName, originalPath, code);
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    // Implementations
    /**
     * A source file where the code has been preloaded.
     */
    static class Preloaded extends SourceFile {

        private static final long serialVersionUID = 1L;

        Preloaded(String fileName, String originalPath, String code) {
            super(fileName);
            super.setOriginalPath(originalPath);
            super.setCode(code);
        }
    }
}
