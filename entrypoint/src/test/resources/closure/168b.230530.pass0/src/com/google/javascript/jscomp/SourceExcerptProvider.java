/*
 * Copyright 2007 The Closure Compiler Authors.
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

/**
 * A source excerpt provider is responsible for building source code excerpt
 * of specific locations, such as a specific line or a region around a
 * given line number.
 */
public interface SourceExcerptProvider {

    /**
     * Source excerpt variety.
     */
    enum SourceExcerpt {

        /**
         * Line excerpt.
         */
        LINE
    }

    /**
     * Get the line indicated by the line number. This call will return only the
     * specific line.
     *
     * @param lineNumber the line number, 1 being the first line of the file
     * @return the line indicated, or {@code null} if it does not exist
     */
    String getSourceLine(String sourceName, int lineNumber);

    /**
     * A excerpt formatter is responsible of formatting source excerpts.
     */
    interface ExcerptFormatter {
    }
}
