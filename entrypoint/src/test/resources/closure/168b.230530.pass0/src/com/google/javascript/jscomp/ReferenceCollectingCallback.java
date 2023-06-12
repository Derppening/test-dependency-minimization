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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticReference;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import com.google.javascript.rhino.jstype.StaticSymbolTable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A helper class for passes that want to access all information about where a
 * variable is referenced and declared at once and then make a decision as to
 * how it should be handled, possibly inlining, reordering, or generating
 * warnings. Callers do this by providing {@link Behavior} and then
 * calling {@link #process(Node, Node)}.
 *
 * @author kushal@google.com (Kushal Dave)
 */
class ReferenceCollectingCallback implements ScopedCallback, HotSwapCompilerPass, StaticSymbolTable<Var, ReferenceCollectingCallback.Reference> {

    /**
     * Source of behavior at various points in the traversal.
     */
    private final Behavior behavior;

    /**
     * JavaScript compiler to use in traversing.
     */
    private final AbstractCompiler compiler;

    /**
     * Only collect references for filtered variables.
     */
    private final Predicate<Var> varFilter;

    /**
     * Constructor only collects references that match the given variable.
     *
     * The test for Var equality uses reference equality, so it's necessary to
     * inject a scope when you traverse.
     */
    ReferenceCollectingCallback(AbstractCompiler compiler, Behavior behavior, Predicate<Var> varFilter) {
        this.compiler = null;
        this.behavior = null;
        this.varFilter = null;
        throw new AssertionError("This method should not be reached! Signature: ReferenceCollectingCallback(AbstractCompiler, Behavior, Predicate)");
    }

    /**
     * Convenience method for running this pass over a tree with this
     * class as a callback.
     */
    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }

    /**
     * For each node, update the block stack and reference collection
     * as appropriate.
     */
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: visit(NodeTraversal, Node, Node)");
    }

    /**
     * Updates block stack and invokes any additional behavior.
     */
    @Override
    public void enterScope(NodeTraversal t) {
        throw new AssertionError("This method should not be reached! Signature: enterScope(NodeTraversal)");
    }

    /**
     * Updates block stack and invokes any additional behavior.
     */
    @Override
    public void exitScope(NodeTraversal t) {
        throw new AssertionError("This method should not be reached! Signature: exitScope(NodeTraversal)");
    }

    /**
     * Updates block stack.
     */
    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: shouldTraverse(NodeTraversal, Node, Node)");
    }

    interface ReferenceMap {
    }

    /**
     * Way for callers to add specific behavior during traversal that
     * utilizes the built-up reference information.
     */
    interface Behavior {
    }

    static Behavior DO_NOTHING_BEHAVIOR = new Behavior() {

        public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {
        }
    };

    /**
     * A collection of references. Can be subclassed to apply checks or
     * store additional state when adding.
     */
    static class ReferenceCollection implements Iterable<Reference> {

        List<Reference> references = Lists.newArrayList();

        @Override
        public Iterator<Reference> iterator() {
            return references.iterator();
        }
    }

    /**
     * Represents a single declaration or reference to a variable.
     */
    static final class Reference implements StaticReference<JSType> {

        private static final Set<Integer> DECLARATION_PARENTS = ImmutableSet.of(Token.VAR, Token.FUNCTION, Token.CATCH);

        private final Node nameNode;

        private final BasicBlock basicBlock;

        private final Scope scope;

        private final InputId inputId;

        private final StaticSourceFile sourceFile;

        // Bleeding functions are weird, because the declaration does
        private Reference(Node nameNode, BasicBlock basicBlock, Scope scope, InputId inputId) {
            this.nameNode = nameNode;
            this.basicBlock = basicBlock;
            this.scope = scope;
            this.inputId = inputId;
            this.sourceFile = nameNode.getStaticSourceFile();
        }

        @Override
        public Node getNode() {
            throw new AssertionError("This method should not be reached! Signature: getNode()");
        }

        public InputId getInputId() {
            return inputId;
        }
    }

    /**
     * Represents a section of code that is uninterrupted by control structures
     * (conditional or iterative logic).
     */
    static final class BasicBlock {

        private final BasicBlock parent;

        /**
         * Determines whether the block may not be part of the normal control flow,
         * but instead "hoisted" to the top of the scope.
         */
        private final boolean isHoisted;

        /**
         * Whether this block denotes a function scope.
         */
        private final boolean isFunction;

        /**
         * Whether this block denotes a loop.
         */
        private final boolean isLoop;

        /**
         * Creates a new block.
         * @param parent The containing block.
         * @param root The root node of the block.
         */
        BasicBlock(BasicBlock parent, Node root) {
            this.parent = parent;
            // only named functions may be hoisted.
            this.isHoisted = NodeUtil.isHoistedFunctionDeclaration(root);
            this.isFunction = root.isFunction();
            if (root.getParent() != null) {
                int pType = root.getParent().getType();
                this.isLoop = pType == Token.DO || pType == Token.WHILE || pType == Token.FOR;
            } else {
                this.isLoop = false;
            }
        }
    }
}
