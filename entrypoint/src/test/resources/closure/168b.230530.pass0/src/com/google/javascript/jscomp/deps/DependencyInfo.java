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
package com.google.javascript.jscomp.deps;

import java.util.Collection;

/**
 * A data structure for JS dependency information for a single .js file.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public interface DependencyInfo {

    /**
     * Gets the symbols provided by this file.
     */
    public Collection<String> getProvides();

    /**
     * Gets the symbols required by this file.
     */
    public Collection<String> getRequires();
}
