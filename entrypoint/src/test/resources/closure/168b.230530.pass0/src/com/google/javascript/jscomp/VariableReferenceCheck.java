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

import com.google.javascript.rhino.Node;

/**
 * Checks variables to see if they are referenced before their declaration, or
 * if they are redeclared in a way that is suspicious (i.e. not dictated by
 * control structures). This is a more aggressive version of {@link VarCheck},
 * but it lacks the cross-module checks.
 *
 * @author kushal@google.com (Kushal Dave)
 */
class VariableReferenceCheck implements HotSwapCompilerPass {

    static final DiagnosticType UNDECLARED_REFERENCE = DiagnosticType.warning("JSC_REFERENCE_BEFORE_DECLARE", "Variable referenced before declaration: {0}");

    static final DiagnosticType REDECLARED_VARIABLE = DiagnosticType.warning("JSC_REDECLARED_VARIABLE", "Redeclared variable: {0}");

    static final DiagnosticType AMBIGUOUS_FUNCTION_DECL = DiagnosticType.disabled("AMBIGUOUS_FUNCTION_DECL", "Ambiguous use of a named function: {0}.");

    private final AbstractCompiler compiler;

    private final CheckLevel checkLevel;

    // NOTE(nicksantos): It's a lot faster to use a shared Set that
    public VariableReferenceCheck(AbstractCompiler compiler, CheckLevel checkLevel) {
        this.compiler = null;
        this.checkLevel = null;
        throw new AssertionError("This method should not be reached! Signature: VariableReferenceCheck(AbstractCompiler, CheckLevel)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }
}
