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

import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;

/**
 * Use {@link ControlFlowGraph} and {@link GraphReachability} to inform user
 * about unreachable code.
 */
class CheckUnreachableCode implements ScopedCallback {

    static final DiagnosticType UNREACHABLE_CODE = DiagnosticType.error("JSC_UNREACHABLE_CODE", "unreachable code");

    private final AbstractCompiler compiler;

    private final CheckLevel level;

    CheckUnreachableCode(AbstractCompiler compiler, CheckLevel level) {
        this.compiler = null;
        this.level = null;
        throw new AssertionError("This method should not be reached! Signature: CheckUnreachableCode(AbstractCompiler, CheckLevel)");
    }

    @Override
    public void enterScope(NodeTraversal t) {
        throw new AssertionError("This method should not be reached! Signature: enterScope(NodeTraversal)");
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: shouldTraverse(NodeTraversal, Node, Node)");
    }

    @Override
    public void exitScope(NodeTraversal t) {
        throw new AssertionError("This method should not be reached! Signature: exitScope(NodeTraversal)");
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: visit(NodeTraversal, Node, Node)");
    }
}
