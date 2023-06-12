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
import java.util.*;

/**
 * Verifies that constants are only assigned a value once.
 * e.g. var XX = 5;
 * XX = 3;    // error!
 * XX++;      // error!
 */
class ConstCheck extends AbstractPostOrderCallback implements CompilerPass {

    static final DiagnosticType CONST_REASSIGNED_VALUE_ERROR = DiagnosticType.error("JSC_CONSTANT_REASSIGNED_VALUE_ERROR", "constant {0} assigned a value more than once");

    private final AbstractCompiler compiler;

    private final Set<Scope.Var> initializedConstants;

    /**
     * Creates an instance.
     */
    public ConstCheck(AbstractCompiler compiler) {
        this.compiler = null;
        this.initializedConstants = null;
        throw new AssertionError("This method should not be reached! Signature: ConstCheck(AbstractCompiler)");
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
