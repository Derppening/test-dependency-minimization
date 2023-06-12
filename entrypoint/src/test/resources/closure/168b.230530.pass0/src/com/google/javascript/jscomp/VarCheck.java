/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Checks that all variables are declared, that file-private variables are
 * accessed only in the file that declares them, and that any var references
 * that cross module boundaries respect declared module dependencies.
 */
class VarCheck extends AbstractPostOrderCallback implements HotSwapCompilerPass {

    static final DiagnosticType UNDEFINED_VAR_ERROR = DiagnosticType.error("JSC_UNDEFINED_VARIABLE", "variable {0} is undeclared");

    static final DiagnosticType VIOLATED_MODULE_DEP_ERROR = DiagnosticType.error("JSC_VIOLATED_MODULE_DEPENDENCY", "module {0} cannot reference {2}, defined in " + "module {1}, since {1} loads after {0}");

    static final DiagnosticType MISSING_MODULE_DEP_ERROR = DiagnosticType.warning("JSC_MISSING_MODULE_DEPENDENCY", "missing module dependency; module {0} should depend " + "on module {1} because it references {2}");

    static final DiagnosticType STRICT_MODULE_DEP_ERROR = DiagnosticType.disabled("JSC_STRICT_MODULE_DEPENDENCY", "module {0} cannot reference {2}, defined in " + "module {1}");

    static final DiagnosticType NAME_REFERENCE_IN_EXTERNS_ERROR = DiagnosticType.warning("JSC_NAME_REFERENCE_IN_EXTERNS", "accessing name {0} in externs has no effect");

    static final DiagnosticType UNDEFINED_EXTERN_VAR_ERROR = DiagnosticType.warning("JSC_UNDEFINED_EXTERN_VAR_ERROR", "name {0} is not undefined in the externs.");

    // Vars that still need to be declared in externs. These will be declared
    // at the end of the pass, or when we see the equivalent var declared
    private final AbstractCompiler compiler;

    // Whether this is the post-processing sanity check.
    private final boolean sanityCheck;

    // Whether extern checks emit error.
    private final boolean strictExternCheck;

    VarCheck(AbstractCompiler compiler, boolean sanityCheck) {
        this.compiler = null;
        this.strictExternCheck = false;
        this.sanityCheck = false;
        throw new AssertionError("This method should not be reached! Signature: VarCheck(AbstractCompiler, boolean)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: visit(NodeTraversal, Node, Node)");
    }
}
