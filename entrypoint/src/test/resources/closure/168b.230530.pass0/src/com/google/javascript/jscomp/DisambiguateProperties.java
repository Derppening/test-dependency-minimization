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
import com.google.javascript.rhino.Node;
import java.util.Map;
import java.util.logging.Logger;

/**
 * DisambiguateProperties renames properties to disambiguate between unrelated
 * fields with the same name. Two properties are considered related if they
 * share a definition on their prototype chains, or if they are potentially
 * referenced together via union types.
 *
 * <p> Renamimg only occurs if there are two or more distinct properties with
 * the same name.
 *
 * <p> This pass allows other passes, such as inlining and code removal to take
 * advantage of type information implicitly.
 *
 * <pre>
 *   Foo.a;
 *   Bar.a;
 * </pre>
 *
 * <p> will become
 *
 * <pre>
 *   Foo.a$Foo;
 *   Bar.a$Bar;
 * </pre>
 */
class DisambiguateProperties<T> implements CompilerPass {

    // To prevent the logs from filling up, we cap the number of warnings
    // that we tell the user to fix per-property.
    private static final int MAX_INVALDIATION_WARNINGS_PER_PROPERTY = 10;

    private static final Logger logger = Logger.getLogger(DisambiguateProperties.class.getName());

    static class Warnings {

        // TODO(user): {1} and {2} are not exactly useful for most people.
        static final DiagnosticType INVALIDATION = DiagnosticType.disabled("JSC_INVALIDATION", "Property disambiguator skipping all instances of property {0} " + "because of type {1} node {2}. {3}");
    }

    private final AbstractCompiler compiler;

    private final TypeSystem<T> typeSystem;

    /**
     * Map of a type to all the related errors that invalidated the type
     * for disambiguation. It has be Object because of the generic nature of
     * this pass.
     */
    private Multimap<Object, JSError> invalidationMap;

    /**
     * In practice any large code base will have thousands and thousands of
     * type invalidations, which makes reporting all of the errors useless.
     * However, certain properties are worth specifically guarding because of the
     * large amount of code that can be removed as dead code. This list contains
     * the properties (eg: "toString") that we care about; if any of these
     * properties is invalidated it causes an error.
     */
    private final Map<String, CheckLevel> propertiesToErrorFor;

    /**
     * This constructor should only be called by one of the helper functions
     * above for either the JSType system, or the concrete type system.
     */
    private DisambiguateProperties(AbstractCompiler compiler, TypeSystem<T> typeSystem, Map<String, CheckLevel> propertiesToErrorFor) {
        this.compiler = null;
        this.typeSystem = null;
        this.propertiesToErrorFor = null;
        throw new AssertionError("This method should not be reached! Signature: DisambiguateProperties(AbstractCompiler, TypeSystem, Map)");
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }

    /**
     * Interface for providing the type information needed by this pass.
     */
    private interface TypeSystem<T> {
        // TODO(user): add a getUniqueName(T type) method that is guaranteed
        // to be unique, performant and human-readable.
    }
}
