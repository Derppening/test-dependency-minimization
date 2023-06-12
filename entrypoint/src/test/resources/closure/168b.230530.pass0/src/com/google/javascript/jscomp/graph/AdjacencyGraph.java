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
package com.google.javascript.jscomp.graph;

/**
 * A minimal graph interface.  Provided is add nodes to the graph, adjacency
 * calculation between a SubGraph and a GraphNode, and adding node annotations.
 *
 * <p>For a more extensive interface, see {@link Graph}.
 *
 * @param <N> Value type that the graph node stores.
 * @param <E> Value type that the graph edge stores.
 * @see Graph
 */
public interface AdjacencyGraph<N, E> {

    /**
     * Gets a node from the graph given a value. Values equality are compared
     * using <code>Object.equals</code>.
     *
     * @param value The node's value.
     * @return The corresponding node in the graph, null if there value has no
     *         corresponding node.
     */
    GraphNode<N, E> getNode(N value);
}
