/*
 * Copyright 2005 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.deps.DependencyInfo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A JavaScript module has a unique name, consists of a list of compiler inputs,
 * and can depend on other modules.
 */
public class JSModule implements DependencyInfo, Serializable {

    private static final long serialVersionUID = 1;

    static final DiagnosticType CIRCULAR_DEPENDENCY_ERROR = DiagnosticType.error("JSC_CIRCULAR_DEP", "Circular dependency detected: {0}");

    /**
     * Module name
     */
    private final String name;

    /**
     * Source code inputs
     */
    private final List<CompilerInput> inputs = new ArrayList<CompilerInput>();

    /**
     * Modules that this module depends on
     */
    private final List<JSModule> deps = new ArrayList<JSModule>();

    private int depth;

    /**
     * Creates an instance.
     *
     * @param name A unique name for the module
     */
    public JSModule(String name) {
        this.name = name;
        this.depth = -1;
    }

    /**
     * Gets the module name.
     */
    public String getName() {
        return name;
    }

    @Override
    public List<String> getProvides() {
        return ImmutableList.<String>of(name);
    }

    @Override
    public List<String> getRequires() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (JSModule m : deps) {
            builder.add(m.getName());
        }
        return builder.build();
    }

    /**
     * Adds a source file input to this module.
     */
    public void add(SourceFile file) {
        add(new CompilerInput(file));
    }

    /**
     * Adds a source code input to this module.
     */
    public void add(CompilerInput input) {
        inputs.add(input);
        input.setModule(this);
    }

    /**
     * Gets the list of modules that this module depends on.
     *
     * @return A list that may be empty but not null
     */
    public List<JSModule> getDependencies() {
        return deps;
    }

    /**
     * Gets this module's list of source code inputs.
     *
     * @return A list that may be empty but not null
     */
    public List<CompilerInput> getInputs() {
        return inputs;
    }

    /**
     * Returns the module name (primarily for debugging).
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * @param dep the depth to set
     */
    public void setDepth(int dep) {
        this.depth = dep;
    }

    /**
     * @return the depth
     */
    public int getDepth() {
        return depth;
    }
}
