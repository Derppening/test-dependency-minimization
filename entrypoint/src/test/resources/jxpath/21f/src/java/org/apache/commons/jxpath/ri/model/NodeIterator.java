/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.jxpath.ri.model;

/**
 * Definition for an iterator for all kinds of Nodes.
 *
 * @author Dmitri Plotnikov
 * @version $Revision$ $Date$
 */
public interface NodeIterator {

    /**
     * Get the current iterator position.
     * @return int position
     */
    int getPosition();

    /**
     * Set the new current position.
     * @param position the position to set
     * @return <code>true</code> if there is a node at <code>position</code>.
     */
    boolean setPosition(int position);

    /**
     * Get the NodePointer at the current position.
     * @return NodePointer
     */
    NodePointer getNodePointer();
}
