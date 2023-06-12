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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Checks that the code obeys the static restrictions of strict mode:
 * <ol>
 * <li> No use of "with".
 * <li> No deleting variables, functions, or arguments.
 * <li> No re-declarations or assignments of "eval" or arguments.
 * <li> No use of "eval" (optional check for Caja).
 * </ol>
 */
class StrictModeCheck extends AbstractPostOrderCallback implements CompilerPass {

    static final DiagnosticType UNKNOWN_VARIABLE = DiagnosticType.warning("JSC_UNKNOWN_VARIABLE", "unknown variable {0}");

    static final DiagnosticType EVAL_USE = DiagnosticType.error("JSC_EVAL_USE", "\"eval\" cannot be used in Caja");

    static final DiagnosticType EVAL_DECLARATION = DiagnosticType.warning("JSC_EVAL_DECLARATION", "\"eval\" cannot be redeclared in ES5 strict mode");

    static final DiagnosticType EVAL_ASSIGNMENT = DiagnosticType.warning("JSC_EVAL_ASSIGNMENT", "the \"eval\" object cannot be reassigned in ES5 strict mode");

    static final DiagnosticType ARGUMENTS_DECLARATION = DiagnosticType.warning("JSC_ARGUMENTS_DECLARATION", "\"arguments\" cannot be redeclared in ES5 strict mode");

    static final DiagnosticType ARGUMENTS_ASSIGNMENT = DiagnosticType.warning("JSC_ARGUMENTS_ASSIGNMENT", "the \"arguments\" object cannot be reassigned in ES5 strict mode");

    static final DiagnosticType DELETE_VARIABLE = DiagnosticType.warning("JSC_DELETE_VARIABLE", "variables, functions, and arguments cannot be deleted in " + "ES5 strict mode");

    static final DiagnosticType ILLEGAL_NAME = DiagnosticType.error("JSC_ILLEGAL_NAME", "identifiers ending in '__' cannot be used in Caja");

    static final DiagnosticType DUPLICATE_OBJECT_KEY = DiagnosticType.warning("JSC_DUPLICATE_OBJECT_KEY", "object literals cannot contain duplicate keys in ES5 strict mode");

    private final AbstractCompiler compiler;

    private final boolean noVarCheck;

    private final boolean noCajaChecks;

    StrictModeCheck(AbstractCompiler compiler, boolean noVarCheck, boolean noCajaChecks) {
        this.compiler = null;
        this.noVarCheck = false;
        this.noCajaChecks = false;
        throw new AssertionError("This method should not be reached! Signature: StrictModeCheck(AbstractCompiler, boolean, boolean)");
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
