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
package com.google.debugging.sourcemap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes.
 */
public class SourceMapGeneratorV2 implements SourceMapGenerator {

    private boolean validate = false;

    private static final int UNMAPPED = -1;

    /**
     * A pre-order traversal ordered list of mappings stored in this map.
     */
    private List<Mapping> mappings = Lists.newArrayList();

    /**
     * A map of source names to source name index
     */
    private LinkedHashMap<String, Integer> sourceFileMap = Maps.newLinkedHashMap();

    /**
     * A map of symbol names to symbol name index
     */
    private LinkedHashMap<String, Integer> originalNameMap = Maps.newLinkedHashMap();

    /**
     * Cache of the last mappings source name.
     */
    private String lastSourceFile = null;

    /**
     * Cache of the last mappings source name index.
     */
    private int lastSourceFileIndex = -1;

    /**
     * For validation store the last mapping added.
     */
    private Mapping lastMapping;

    /**
     * The position that the current source map is offset in the
     * buffer being used to generated the compiled source file.
     */
    private FilePosition offsetPosition = new FilePosition(0, 0);

    /**
     * The position that the current source map is offset in the
     * generated the compiled source file by the addition of a
     * an output wrapper prefix.
     */
    private FilePosition prefixPosition = new FilePosition(0, 0);

    /**
     * Adds a mapping for the given node.  Mappings must be added in order.
     * @param startPosition The position on the starting line
     * @param endPosition The position on the ending line.
     */
    @Override
    public void addMapping(String sourceName, @Nullable String symbolName, FilePosition sourceStartPosition, FilePosition startPosition, FilePosition endPosition) {
        // Don't bother if there is not sufficient information to be useful.
        if (sourceName == null || sourceStartPosition.getLine() < 0) {
            return;
        }
        FilePosition adjustedStart = startPosition;
        FilePosition adjustedEnd = endPosition;
        if (offsetPosition.getLine() != 0 || offsetPosition.getColumn() != 0) {
            // If the mapping is found on the first line, we need to offset
            // its character position by the number of characters found on
            // the *last* line of the source file to which the code is
            // being generated.
            int offsetLine = offsetPosition.getLine();
            int startOffsetPosition = offsetPosition.getColumn();
            int endOffsetPosition = offsetPosition.getColumn();
            if (startPosition.getLine() > 0) {
                startOffsetPosition = 0;
            }
            if (endPosition.getLine() > 0) {
                endOffsetPosition = 0;
            }
            adjustedStart = new FilePosition(startPosition.getLine() + offsetLine, startPosition.getColumn() + startOffsetPosition);
            adjustedEnd = new FilePosition(endPosition.getLine() + offsetLine, endPosition.getColumn() + endOffsetPosition);
        }
        // Create the new mapping.
        Mapping mapping = new Mapping();
        mapping.sourceFile = getSourceId(sourceName);
        mapping.originalPosition = sourceStartPosition;
        mapping.originalName = symbolName;
        mapping.startPosition = adjustedStart;
        mapping.endPosition = adjustedEnd;
        // Validate the mappings are in a proper order.
        if (lastMapping != null) {
            int lastLine = lastMapping.startPosition.getLine();
            int lastColumn = lastMapping.startPosition.getColumn();
            int nextLine = mapping.startPosition.getLine();
            int nextColumn = mapping.startPosition.getColumn();
            Preconditions.checkState(nextLine > lastLine || (nextLine == lastLine && nextColumn >= lastColumn), "Incorrect source mappings order, previous : (%s,%s)\n" + "new : (%s,%s)\nnode : %s", lastLine, lastColumn, nextLine, nextColumn);
        }
        lastMapping = mapping;
        mappings.add(mapping);
    }

    // Source map field helpers.
    /**
     * Pools source names.
     * @param sourceName The source location to index.
     * @return The id to represent the source name in the output.
     */
    private int getSourceId(String sourceName) {
        if (sourceName != lastSourceFile) {
            lastSourceFile = sourceName;
            Integer index = sourceFileMap.get(sourceName);
            if (index != null) {
                lastSourceFileIndex = index;
            } else {
                lastSourceFileIndex = sourceFileMap.size();
                sourceFileMap.put(sourceName, lastSourceFileIndex);
            }
        }
        return lastSourceFileIndex;
    }

    /**
     * A mapping from a given position in an input source file to a given position
     * in the generated code.
     */
    static class Mapping {

        /**
         * A unique ID for this mapping for record keeping purposes.
         */
        int id = UNMAPPED;

        /**
         * The source file index.
         */
        int sourceFile;

        /**
         * The position of the code in the input source file. Both
         * the line number and the character index are indexed by
         * 1 for legacy reasons via the Rhino Node class.
         */
        FilePosition originalPosition;

        /**
         * The starting position of the code in the generated source
         * file which this mapping represents. Indexed by 0.
         */
        FilePosition startPosition;

        /**
         * The ending position of the code in the generated source
         * file which this mapping represents. Indexed by 0.
         */
        FilePosition endPosition;

        /**
         * The original name of the token found at the position
         * represented by this mapping (if any).
         */
        String originalName;

        /**
         * Whether the mapping is actually used by the source map.
         */
        boolean used = false;
    }
}
