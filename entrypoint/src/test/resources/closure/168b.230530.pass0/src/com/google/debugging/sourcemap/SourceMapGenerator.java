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

import javax.annotation.Nullable;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes
 *
 * @author johnlenz@google.com (John Lenz)
 */
public interface SourceMapGenerator {

    /**
     * Adds a mapping for the given node.  Mappings must be added in order.
     * @param sourceName The file name to use in the generate source map
     *     to represent this source.
     * @param symbolName The symbol name associated with this position in the
     *     source map.
     * @param sourceStartPosition The starting position in the original source for
     *     represented range outputStartPosition to outputEndPosition in the
     *     generated file.
     * @param outputStartPosition The position on the starting line
     * @param outputEndPosition The position on the ending line.
     */
    void addMapping(String sourceName, @Nullable String symbolName, FilePosition sourceStartPosition, FilePosition outputStartPosition, FilePosition outputEndPosition);
}
