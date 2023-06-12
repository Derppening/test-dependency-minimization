/*
 * Copyright 2011 The Closure Compiler Authors.
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
import java.io.File;

/**
 * Rewrites a CommonJS module http://wiki.commonjs.org/wiki/Modules/1.1.1
 * into a form that can be safely concatenated.
 * Does not add a function around the module body but instead adds suffixes
 * to global variables to avoid conflicts.
 * Calls to require are changed to reference the required module directly.
 * goog.provide and goog.require are emitted for closure compiler automatic
 * ordering.
 */
public class ProcessCommonJSModules implements CompilerPass {

    public static final String DEFAULT_FILENAME_PREFIX = "." + File.separator;

    private static final String MODULE_NAME_SEPARATOR = "\\$";

    private static final String MODULE_NAME_PREFIX = "module$";

    private final AbstractCompiler compiler;

    private final String filenamePrefix;

    private final boolean reportDependencies;

    ProcessCommonJSModules(AbstractCompiler compiler, String filenamePrefix, boolean reportDependencies) {
        this.compiler = null;
        this.filenamePrefix = null;
        this.reportDependencies = false;
        throw new AssertionError("This method should not be reached! Signature: ProcessCommonJSModules(AbstractCompiler, String, boolean)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }
}
