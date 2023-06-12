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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link JSModule} dependency graph that assigns a depth to each module and
 * can answer depth-related queries about them. For the purposes of this class,
 * a module's depth is defined as the number of hops in the longest path from
 * the module to a module with no dependencies.
 */
public class JSModuleGraph {

    private List<JSModule> modules;

    /**
     * Lists of modules at each depth. <code>modulesByDepth.get(3)</code> is a
     * list of the modules at depth 3, for example.
     */
    private List<List<JSModule>> modulesByDepth;

    /**
     * dependencyMap is a cache of dependencies that makes the dependsOn
     * function faster.  Each map entry associates a starting
     * JSModule with the set of JSModules that are transitively dependent on the
     * starting module.
     *
     * If the cache returns null, then the entry hasn't been filled in for that
     * module.
     *
     * dependencyMap should be filled from leaf to root so that
     * getTransitiveDepsDeepestFirst can use its results directly.
     */
    private Map<JSModule, Set<JSModule>> dependencyMap = Maps.newHashMap();

    /**
     * Creates a module graph from a list of modules in dependency order.
     */
    public JSModuleGraph(List<JSModule> modulesInDepOrder) {
        Preconditions.checkState(modulesInDepOrder.size() == Sets.newHashSet(modulesInDepOrder).size(), "Found duplicate modules");
        modules = ImmutableList.copyOf(modulesInDepOrder);
        modulesByDepth = Lists.newArrayList();
        for (JSModule module : modulesInDepOrder) {
            int depth = 0;
            for (JSModule dep : module.getDependencies()) {
                int depDepth = dep.getDepth();
                if (depDepth < 0) {
                    throw new ModuleDependenceException(String.format("Modules not in dependency order: %s preceded %s", module.getName(), dep.getName()), module, dep);
                }
                depth = Math.max(depth, depDepth + 1);
            }
            module.setDepth(depth);
            if (depth == modulesByDepth.size()) {
                modulesByDepth.add(new ArrayList<JSModule>());
            }
            modulesByDepth.get(depth).add(module);
        }
    }

    /*
   * Exception class for declaring when the modules being fed into a
   * JSModuleGraph as input aren't in dependence order, and so can't be
   * processed for caching of various dependency-related queries.
   */
    protected static class ModuleDependenceException extends IllegalArgumentException {

        private static final long serialVersionUID = 1;

        private final JSModule module;

        private final JSModule dependentModule;

        protected ModuleDependenceException(String message, JSModule module, JSModule dependentModule) {
            super(message);
            this.module = module;
            this.dependentModule = dependentModule;
        }

        public JSModule getModule() {
            return module;
        }

        public JSModule getDependentModule() {
            return dependentModule;
        }
    }
}
