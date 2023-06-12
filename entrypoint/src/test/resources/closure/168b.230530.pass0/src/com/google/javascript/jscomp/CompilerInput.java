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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.JsFileParser;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A class for the internal representation of an input to the compiler.
 * Wraps a {@link SourceAst} and maintain state such as module for the input and
 * whether the input is an extern. Also calculates provided and required types.
 */
public class CompilerInput implements SourceAst, DependencyInfo {

    private static final long serialVersionUID = 1L;

    // Info about where the file lives.
    private JSModule module;

    final private InputId id;

    // The AST.
    private final SourceAst ast;

    // Provided and required symbols.
    private final Set<String> provides = Sets.newHashSet();

    private final Set<String> requires = Sets.newHashSet();

    private boolean generatedDependencyInfoFromSource = false;

    // An AbstractCompiler for doing parsing.
    // We do not want to persist this across serialized state.
    private transient AbstractCompiler compiler;

    public CompilerInput(SourceAst ast, boolean isExtern) {
        this(ast, ast.getInputId(), isExtern);
    }

    public CompilerInput(SourceAst ast, InputId inputId, boolean isExtern) {
        this.ast = ast;
        this.id = inputId;
        // TODO(nicksantos): Add a precondition check here. People are passing
        // in null, but they should not be.
        if (ast != null && ast.getSourceFile() != null) {
            ast.getSourceFile().setIsExtern(isExtern);
        }
    }

    public CompilerInput(SourceFile file) {
        this(file, false);
    }

    public CompilerInput(SourceFile file, boolean isExtern) {
        this(new JsAst(file), isExtern);
    }

    /**
     * Returns a name for this input. Must be unique across all inputs.
     */
    @Override
    public InputId getInputId() {
        return id;
    }

    /**
     * Returns a name for this input. Must be unique across all inputs.
     */
    public String getName() {
        return id.getIdName();
    }

    @Override
    public Node getAstRoot(AbstractCompiler compiler) {
        Node root = ast.getAstRoot(compiler);
        // The root maybe null if the AST can not be created.
        if (root != null) {
            Preconditions.checkState(root.isScript());
            Preconditions.checkNotNull(root.getInputId());
        }
        return root;
    }

    @Override
    public SourceFile getSourceFile() {
        return ast.getSourceFile();
    }

    /**
     * Sets an abstract compiler for doing parsing.
     */
    public void setCompiler(AbstractCompiler compiler) {
        this.compiler = compiler;
    }

    private void checkErrorManager() {
        Preconditions.checkNotNull(compiler, "Expected setCompiler to be called first: " + this);
        Preconditions.checkNotNull(compiler.getErrorManager(), "Expected compiler to call an error manager: " + this);
    }

    /**
     * Gets a list of types depended on by this input.
     */
    @Override
    public Collection<String> getRequires() {
        checkErrorManager();
        try {
            regenerateDependencyInfoIfNecessary();
            return Collections.<String>unmodifiableSet(requires);
        } catch (IOException e) {
            compiler.getErrorManager().report(CheckLevel.ERROR, JSError.make(AbstractCompiler.READ_ERROR, getName()));
            return ImmutableList.<String>of();
        }
    }

    /**
     * Gets a list of types provided by this input.
     */
    @Override
    public Collection<String> getProvides() {
        checkErrorManager();
        try {
            regenerateDependencyInfoIfNecessary();
            return Collections.<String>unmodifiableSet(provides);
        } catch (IOException e) {
            compiler.getErrorManager().report(CheckLevel.ERROR, JSError.make(AbstractCompiler.READ_ERROR, getName()));
            return ImmutableList.<String>of();
        }
    }

    // TODO(nicksantos): Remove addProvide/addRequire/removeRequire once
    /**
     * Regenerates the provides/requires if we need to do so.
     */
    private void regenerateDependencyInfoIfNecessary() throws IOException {
        // If the code is NOT a JsAst, then it was not originally JS code.
        // Look at the Ast for dependency info.
        if (!(ast instanceof JsAst)) {
            Preconditions.checkNotNull(compiler, "Expected setCompiler to be called first");
            DepsFinder finder = new DepsFinder();
            Node root = getAstRoot(compiler);
            if (root == null) {
                return;
            }
            finder.visitTree(getAstRoot(compiler));
            // TODO(nicksantos|user): This caching behavior is a bit
            // odd, and only works if you assume the exact call flow that
            // clients are currently using.  In that flow, they call
            // getProvides(), then remove the goog.provide calls from the
            // AST, and then call getProvides() again.
            //
            // This won't work for any other call flow, or any sort of incremental
            // compilation scheme. The API needs to be fixed so callers aren't
            // doing weird things like this, and then we should get rid of the
            // multiple-scan strategy.
            provides.addAll(finder.provides);
            requires.addAll(finder.requires);
        } else {
            // Otherwise, look at the source code.
            if (!generatedDependencyInfoFromSource) {
                // Note: it's OK to use getName() instead of
                // getPathRelativeToClosureBase() here because we're not using
                // this to generate deps files. (We're only using it for
                // symbol dependencies.)
                DependencyInfo info = (new JsFileParser(compiler.getErrorManager())).setIncludeGoogBase(true).parseFile(getName(), getName(), getCode());
                provides.addAll(info.getProvides());
                requires.addAll(info.getRequires());
                generatedDependencyInfoFromSource = true;
            }
        }
    }

    private static class DepsFinder {

        private final List<String> provides = Lists.newArrayList();

        private final List<String> requires = Lists.newArrayList();

        private final CodingConvention codingConvention = new ClosureCodingConvention();

        void visitTree(Node n) {
            visitSubtree(n, null);
        }

        void visitSubtree(Node n, Node parent) {
            if (n.isCall()) {
                String require = codingConvention.extractClassNameIfRequire(n, parent);
                if (require != null) {
                    requires.add(require);
                }
                String provide = codingConvention.extractClassNameIfProvide(n, parent);
                if (provide != null) {
                    provides.add(provide);
                }
                return;
            } else if (parent != null && !parent.isExprResult() && !parent.isScript()) {
                return;
            }
            for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
                visitSubtree(child, n);
            }
        }
    }

    public String getCode() throws IOException {
        return getSourceFile().getCode();
    }

    /**
     * Sets the module to which the input belongs.
     */
    public void setModule(JSModule module) {
        // An input may only belong to one module.
        Preconditions.checkArgument(module == null || this.module == null || this.module == module);
        this.module = module;
    }

    public boolean isExtern() {
        if (ast == null || ast.getSourceFile() == null) {
            return false;
        }
        return ast.getSourceFile().isExtern();
    }

    @Override
    public String toString() {
        return getName();
    }
}
