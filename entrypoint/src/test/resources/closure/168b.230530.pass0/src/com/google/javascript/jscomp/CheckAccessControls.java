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

import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;

/**
 * A compiler pass that checks that the programmer has obeyed all the access
 * control restrictions indicated by JSDoc annotations, like
 * {@code @private} and {@code @deprecated}.
 *
 * Because access control restrictions are attached to type information,
 * it's important that TypedScopeCreator, TypeInference, and InferJSDocInfo
 * all run before this pass. TypedScopeCreator creates and resolves types,
 * TypeInference propagates those types across the AST, and InferJSDocInfo
 * propagates JSDoc across the types.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class CheckAccessControls implements ScopedCallback, HotSwapCompilerPass {

    static final DiagnosticType DEPRECATED_NAME = DiagnosticType.disabled("JSC_DEPRECATED_VAR", "Variable {0} has been deprecated.");

    static final DiagnosticType DEPRECATED_NAME_REASON = DiagnosticType.disabled("JSC_DEPRECATED_VAR_REASON", "Variable {0} has been deprecated: {1}");

    static final DiagnosticType DEPRECATED_PROP = DiagnosticType.disabled("JSC_DEPRECATED_PROP", "Property {0} of type {1} has been deprecated.");

    static final DiagnosticType DEPRECATED_PROP_REASON = DiagnosticType.disabled("JSC_DEPRECATED_PROP_REASON", "Property {0} of type {1} has been deprecated: {2}");

    static final DiagnosticType DEPRECATED_CLASS = DiagnosticType.disabled("JSC_DEPRECATED_CLASS", "Class {0} has been deprecated.");

    static final DiagnosticType DEPRECATED_CLASS_REASON = DiagnosticType.disabled("JSC_DEPRECATED_CLASS_REASON", "Class {0} has been deprecated: {1}");

    static final DiagnosticType BAD_PRIVATE_GLOBAL_ACCESS = DiagnosticType.disabled("JSC_BAD_PRIVATE_GLOBAL_ACCESS", "Access to private variable {0} not allowed outside file {1}.");

    static final DiagnosticType BAD_PRIVATE_PROPERTY_ACCESS = DiagnosticType.disabled("JSC_BAD_PRIVATE_PROPERTY_ACCESS", "Access to private property {0} of {1} not allowed here.");

    static final DiagnosticType BAD_PROTECTED_PROPERTY_ACCESS = DiagnosticType.disabled("JSC_BAD_PROTECTED_PROPERTY_ACCESS", "Access to protected property {0} of {1} not allowed here.");

    static final DiagnosticType PRIVATE_OVERRIDE = DiagnosticType.disabled("JSC_PRIVATE_OVERRIDE", "Overriding private property of {0}.");

    static final DiagnosticType VISIBILITY_MISMATCH = DiagnosticType.disabled("JSC_VISIBILITY_MISMATCH", "Overriding {0} property of {1} with {2} property.");

    static final DiagnosticType CONST_PROPERTY_REASSIGNED_VALUE = DiagnosticType.warning("JSC_CONSTANT_PROPERTY_REASSIGNED_VALUE", "constant property {0} assigned a value more than once");

    static final DiagnosticType CONST_PROPERTY_DELETED = DiagnosticType.warning("JSC_CONSTANT_PROPERTY_DELETED", "constant property {0} cannot be deleted");

    private final AbstractCompiler compiler;

    private final TypeValidator validator;

    private final Multimap<String, String> initializedConstantProperties;

    CheckAccessControls(AbstractCompiler compiler) {
        this.compiler = null;
        this.validator = null;
        this.initializedConstantProperties = null;
        throw new AssertionError("This method should not be reached! Signature: CheckAccessControls(AbstractCompiler)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }

    @Override
    public void enterScope(NodeTraversal t) {
        throw new AssertionError("This method should not be reached! Signature: enterScope(NodeTraversal)");
    }

    @Override
    public void exitScope(NodeTraversal t) {
        throw new AssertionError("This method should not be reached! Signature: exitScope(NodeTraversal)");
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: shouldTraverse(NodeTraversal, Node, Node)");
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: visit(NodeTraversal, Node, Node)");
    }
}
