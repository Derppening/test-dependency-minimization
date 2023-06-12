/*
 * Copyright 2006 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Checks for non side effecting statements such as
 * <pre>
 * var s = "this string is "
 *         "continued on the next line but you forgot the +";
 * x == foo();  // should that be '='?
 * foo();;  // probably just a stray-semicolon. Doesn't hurt to check though
 * </p>
 * and generates warnings.
 */
final class CheckSideEffects extends AbstractPostOrderCallback implements HotSwapCompilerPass {

    static final DiagnosticType USELESS_CODE_ERROR = DiagnosticType.warning("JSC_USELESS_CODE", "Suspicious code. {0}");

    static final String PROTECTOR_FN = "JSCOMPILER_PRESERVE";

    private final CheckLevel level;

    private final AbstractCompiler compiler;

    private final boolean protectSideEffectFreeCode;

    CheckSideEffects(AbstractCompiler compiler, CheckLevel level, boolean protectSideEffectFreeCode) {
        this.compiler = null;
        this.level = null;
        this.protectSideEffectFreeCode = false;
        throw new AssertionError("This method should not be reached! Signature: CheckSideEffects(AbstractCompiler, CheckLevel, boolean)");
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
