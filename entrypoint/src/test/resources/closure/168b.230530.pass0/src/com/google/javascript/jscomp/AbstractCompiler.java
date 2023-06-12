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

import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

/**
 * An abstract compiler, to help remove the circular dependency of
 * passes on JSCompiler.
 *
 * This is an abstract class, so that we can make the methods package-private.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public abstract class AbstractCompiler implements SourceExcerptProvider {

    static final DiagnosticType READ_ERROR = DiagnosticType.error("JSC_READ_ERROR", "Cannot read: {0}");

    private LifeCycleStage stage = LifeCycleStage.RAW;

    // TODO(nicksantos): Decide if all of these are really necessary.
    // Many of them are just accessors that should be passed to the
    // CompilerPass's constructor.
    /**
     * Looks up an input (possibly an externs input) by input id.
     * May return null.
     */
    public abstract CompilerInput getInput(InputId inputId);

    /**
     * Looks up a source file by name. May return null.
     */
    abstract SourceFile getSourceFileByName(String sourceName);

    /**
     * Gets a central registry of type information from the compiled JS.
     */
    public abstract JSTypeRegistry getTypeRegistry();

    /**
     * Report an error or warning.
     */
    public abstract void report(JSError error);

    /**
     * Report an internal error.
     */
    abstract void throwInternalError(String msg, Exception cause);

    /**
     * Gets the current coding convention.
     */
    public abstract CodingConvention getCodingConvention();

    /**
     * Gets the central registry of type violations.
     */
    abstract TypeValidator getTypeValidator();

    /**
     * Gets a default error reporter for injecting into Rhino.
     */
    abstract ErrorReporter getDefaultErrorReporter();

    /**
     * @return The current life-cycle stage of the AST we're working on.
     */
    LifeCycleStage getLifeCycleStage() {
        return stage;
    }

    /**
     * @return Whether any errors have been encountered that
     *     should stop the compilation process.
     */
    abstract boolean hasHaltingErrors();

    /**
     * Returns true if compiling in IDE mode.
     */
    abstract boolean isIdeMode();

    /**
     * Returns the parser configuration.
     */
    abstract Config getParserConfig();

    /**
     * Normalizes the types of AST nodes in the given tree, and
     * annotates any nodes to which the coding convention applies so that passes
     * can read the annotations instead of using the coding convention.
     */
    abstract void prepareAst(Node root);

    /**
     * Gets the error manager.
     */
    abstract public ErrorManager getErrorManager();

    /**
     * @return Whether the AST contains references to the RegExp global object
     *     properties.
     */
    abstract boolean hasRegExpGlobalReferences();

    /**
     * @return The error level the given error object will be reported at.
     */
    abstract CheckLevel getErrorLevel(JSError error);

    static enum LifeCycleStage {

        RAW
    }
    // TODO(bashir) It would be good to extract a single dumb data object with
    // only getters and setters that keeps all global information we keep for a
    // compiler instance. Then move some of the functions of this class there.
}
