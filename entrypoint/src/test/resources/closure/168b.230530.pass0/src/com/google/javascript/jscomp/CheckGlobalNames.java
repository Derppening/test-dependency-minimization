/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.rhino.Node;

/**
 * Checks references to undefined properties of global variables.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class CheckGlobalNames implements CompilerPass {

    private final AbstractCompiler compiler;

    private final CodingConvention convention;

    private final CheckLevel level;

    // Warnings
    static final DiagnosticType UNDEFINED_NAME_WARNING = DiagnosticType.warning("JSC_UNDEFINED_NAME", "{0} is never defined");

    static final DiagnosticType NAME_DEFINED_LATE_WARNING = DiagnosticType.warning("JSC_NAME_DEFINED_LATE", "{0} defined before its owner. {1} is defined at {2}:{3}");

    static final DiagnosticType STRICT_MODULE_DEP_QNAME = DiagnosticType.disabled("JSC_STRICT_MODULE_DEP_QNAME", "module {0} cannot reference {2}, defined in " + "module {1}");

    /**
     * Creates a pass to check global name references at the given warning level.
     */
    CheckGlobalNames(AbstractCompiler compiler, CheckLevel level) {
        this.compiler = null;
        this.convention = null;
        this.level = null;
        throw new AssertionError("This method should not be reached! Signature: CheckGlobalNames(AbstractCompiler, CheckLevel)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }
}
