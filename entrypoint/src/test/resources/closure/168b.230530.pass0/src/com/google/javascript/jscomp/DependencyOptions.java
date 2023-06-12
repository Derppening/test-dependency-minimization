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

import com.google.common.collect.Sets;
import java.io.Serializable;
import java.util.Set;

/**
 * Options for how to manage dependencies between input files.
 *
 * Dependency information is usually pulled out from the JS code by
 * looking for primitive dependency functions (like Closure Library's
 * goog.provide/goog.require). Analysis of this dependency information is
 * controlled by {@code CodingConvention}, which lets you define those
 * dependency primitives.
 *
 * This options class determines how we use that dependency information
 * to change how code is built.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class DependencyOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean sortDependencies = false;

    private boolean pruneDependencies = false;

    private boolean dropMoochers = false;

    private final Set<String> entryPoints = Sets.newHashSet();
}
