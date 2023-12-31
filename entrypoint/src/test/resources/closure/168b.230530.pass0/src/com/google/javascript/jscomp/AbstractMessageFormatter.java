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

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Abstract message formatter providing default behavior for implementations
 * of {@link MessageFormatter} needing a {@link SourceExcerptProvider}.
 */
public abstract class AbstractMessageFormatter implements MessageFormatter {

    private final SourceExcerptProvider source;

    private boolean colorize;

    public AbstractMessageFormatter(SourceExcerptProvider source) {
        this.source = source;
    }

    public void setColorize(boolean colorize) {
        this.colorize = colorize;
    }

    private static final Set<String> SUPPORTED_COLOR_TERMINALS = ImmutableSet.of("xterm", "xterm-color", "xterm-256color", "screen-bce");
}
