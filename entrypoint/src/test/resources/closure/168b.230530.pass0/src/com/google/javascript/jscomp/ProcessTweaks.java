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

import com.google.common.base.CharMatcher;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;
import java.util.Map;
import java.util.SortedMap;

/**
 * Process goog.tweak primitives. Checks that:
 * <ul>
 * <li>parameters to goog.tweak.register* are literals of the correct type.
 * <li>the parameter to goog.tweak.get* is a string literal.
 * <li>parameters to goog.tweak.overrideDefaultValue are literals of the correct
 *     type.
 * <li>tweak IDs passed to goog.tweak.get* and goog.tweak.overrideDefaultValue
 *     correspond to registered tweaks.
 * <li>all calls to goog.tweak.register* and goog.tweak.overrideDefaultValue are
 *     within the top-level context.
 * <li>each tweak is registered only once.
 * <li>calls to goog.tweak.overrideDefaultValue occur before the call to the
 *     corresponding goog.tweak.register* function.
 * </ul>
 * @author agrieve@google.com (Andrew Grieve)
 */
class ProcessTweaks implements CompilerPass {

    private final AbstractCompiler compiler;

    private final boolean stripTweaks;

    private final SortedMap<String, Node> compilerDefaultValueOverrides;

    private static final CharMatcher ID_MATCHER = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).or(CharMatcher.anyOf("0123456789_."));

    // Warnings and Errors.
    static final DiagnosticType UNKNOWN_TWEAK_WARNING = DiagnosticType.warning("JSC_UNKNOWN_TWEAK_WARNING", "no tweak registered with ID {0}");

    static final DiagnosticType TWEAK_MULTIPLY_REGISTERED_ERROR = DiagnosticType.error("JSC_TWEAK_MULTIPLY_REGISTERED_ERROR", "Tweak {0} has already been registered.");

    static final DiagnosticType NON_LITERAL_TWEAK_ID_ERROR = DiagnosticType.error("JSC_NON_LITERAL_TWEAK_ID_ERROR", "tweak ID must be a string literal");

    static final DiagnosticType INVALID_TWEAK_DEFAULT_VALUE_WARNING = DiagnosticType.warning("JSC_INVALID_TWEAK_DEFAULT_VALUE_WARNING", "tweak {0} registered with {1} must have a default value that is a " + "literal of type {2}");

    static final DiagnosticType NON_GLOBAL_TWEAK_INIT_ERROR = DiagnosticType.error("JSC_NON_GLOBAL_TWEAK_INIT_ERROR", "tweak declaration {0} must occur in the global scope");

    static final DiagnosticType TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR = DiagnosticType.error("JSC_TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR", "Cannot override the default value of tweak {0} after it has been " + "registered");

    static final DiagnosticType TWEAK_WRONG_GETTER_TYPE_WARNING = DiagnosticType.warning("JSC_TWEAK_WRONG_GETTER_TYPE_WARNING", "tweak getter function {0} used for tweak registered using {1}");

    static final DiagnosticType INVALID_TWEAK_ID_ERROR = DiagnosticType.error("JSC_INVALID_TWEAK_ID_ERROR", "tweak ID contains illegal characters. Only letters, numbers, _ " + "and . are allowed");

    /**
     * An enum of goog.tweak functions.
     */
    private static enum TweakFunction {
        ;

        final String name;

        final String expectedTypeName;

        final int validNodeTypeA;

        final int validNodeTypeB;

        final TweakFunction registerFunction;

        TweakFunction(String name, String expectedTypeName, int validNodeTypeA, int validNodeTypeB, TweakFunction registerFunction) {
            this.name = name;
            this.expectedTypeName = expectedTypeName;
            this.validNodeTypeA = validNodeTypeA;
            this.validNodeTypeB = validNodeTypeB;
            this.registerFunction = registerFunction;
        }

        String getName() {
            return name;
        }
    }

    // A map of function name -> TweakFunction.
    private static final Map<String, TweakFunction> TWEAK_FUNCTIONS_MAP;

    static {
        TWEAK_FUNCTIONS_MAP = Maps.newHashMap();
        for (TweakFunction func : TweakFunction.values()) {
            TWEAK_FUNCTIONS_MAP.put(func.getName(), func);
        }
    }

    ProcessTweaks(AbstractCompiler compiler, boolean stripTweaks, Map<String, Node> compilerDefaultValueOverrides) {
        this.compiler = null;
        this.stripTweaks = false;
        this.compilerDefaultValueOverrides = null;
        throw new AssertionError("This method should not be reached! Signature: ProcessTweaks(AbstractCompiler, boolean, Map)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }
}
