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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.rhino.Node;

/**
 * Insures '@constructor X' has a 'goog.provide("X")' .
 */
class CheckProvides implements HotSwapCompilerPass {

    private final AbstractCompiler compiler;

    private final CheckLevel checkLevel;

    private final CodingConvention codingConvention;

    static final DiagnosticType MISSING_PROVIDE_WARNING = DiagnosticType.disabled("JSC_MISSING_PROVIDE", "missing goog.provide(''{0}'')");

    CheckProvides(AbstractCompiler compiler, CheckLevel checkLevel) {
        this.compiler = null;
        this.checkLevel = null;
        this.codingConvention = null;
        throw new AssertionError("This method should not be reached! Signature: CheckProvides(AbstractCompiler, CheckLevel)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }
}
