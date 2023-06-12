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

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;

/**
 * Checks for certain uses of the {@code this} keyword that are considered
 * unsafe because they are likely to reference the global {@code this} object
 * unintentionally.
 *
 * <p>A use of {@code this} is considered unsafe if it's on the left side of an
 * assignment or a property access, and not inside one of the following:
 * <ol>
 * <li>a prototype method
 * <li>a function annotated with {@code @constructor}
 * <li>a function annotated with {@code @this}.
 * <li>a function where there's no logical place to put a
 *     {@code this} annotation.
 * </ol>
 *
 * <p>Note that this check does not track assignments of {@code this} to
 * variables or objects. The code
 * <pre>
 * function evil() {
 *   var a = this;
 *   a.useful = undefined;
 * }
 * </pre>
 * will not get flagged, even though it is semantically equivalent to
 * <pre>
 * function evil() {
 *   this.useful = undefined;
 * }
 * </pre>
 * which would get flagged.
 */
final class CheckGlobalThis implements Callback {

    static final DiagnosticType GLOBAL_THIS = DiagnosticType.warning("JSC_USED_GLOBAL_THIS", "dangerous use of the global 'this' object");

    private final AbstractCompiler compiler;

    CheckGlobalThis(AbstractCompiler compiler) {
        this.compiler = null;
        throw new AssertionError("This method should not be reached! Signature: CheckGlobalThis(AbstractCompiler)");
    }

    /**
     * Since this pass reports errors only when a global {@code this} keyword
     * is encountered, there is no reason to traverse non global contexts.
     */
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: shouldTraverse(NodeTraversal, Node, Node)");
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: visit(NodeTraversal, Node, Node)");
    }
}
