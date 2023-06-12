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

import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Set;

/**
 * Process variables annotated as {@code @define}. A define is
 * a special constant that may be overridden by later files and
 * manipulated by the compiler, much like C preprocessor {@code #define}s.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class ProcessDefines implements CompilerPass {

    /**
     * Defines in this set will not be flagged with "unknown define" warnings.
     * There are legacy flags that always set these defines, even when they
     * might not be in the binary.
     */
    private static final Set<String> KNOWN_DEFINES = Sets.newHashSet("COMPILED");

    private final AbstractCompiler compiler;

    private final Map<String, Node> dominantReplacements;

    // Warnings
    static final DiagnosticType UNKNOWN_DEFINE_WARNING = DiagnosticType.warning("JSC_UNKNOWN_DEFINE_WARNING", "unknown @define variable {0}");

    // Errors
    static final DiagnosticType INVALID_DEFINE_TYPE_ERROR = DiagnosticType.error("JSC_INVALID_DEFINE_TYPE_ERROR", "@define tag only permits literal types");

    static final DiagnosticType INVALID_DEFINE_INIT_ERROR = DiagnosticType.error("JSC_INVALID_DEFINE_INIT_ERROR", "illegal initialization of @define variable {0}");

    static final DiagnosticType NON_GLOBAL_DEFINE_INIT_ERROR = DiagnosticType.error("JSC_NON_GLOBAL_DEFINE_INIT_ERROR", "@define variable {0} assignment must be global");

    static final DiagnosticType DEFINE_NOT_ASSIGNABLE_ERROR = DiagnosticType.error("JSC_DEFINE_NOT_ASSIGNABLE_ERROR", "@define variable {0} cannot be reassigned due to code at {1}.");

    private static final MessageFormat REASON_DEFINE_NOT_ASSIGNABLE = new MessageFormat("line {0} of {1}");

    /**
     * Create a pass that overrides define constants.
     *
     * TODO(nicksantos): Write a builder to help JSCompiler induce
     *    {@code replacements} from command-line flags
     *
     * @param replacements A hash table of names of defines to their replacements.
     *   All replacements <b>must</b> be literals.
     */
    ProcessDefines(AbstractCompiler compiler, Map<String, Node> replacements) {
        this.compiler = null;
        dominantReplacements = null;
        throw new AssertionError("This method should not be reached! Signature: ProcessDefines(AbstractCompiler, Map)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }
}
