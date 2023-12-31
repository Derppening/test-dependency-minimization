/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.commons.compress.archivers.sevenz;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base Codec class.
 */
abstract class CoderBase {

    private final Class<?>[] acceptableOptions;

    private static final byte[] NONE = new byte[0];

    /**
     * @param acceptableOptions types that can be used as options for this codec.
     */
    protected CoderBase(Class<?>... acceptableOptions) {
        this.acceptableOptions = acceptableOptions;
    }

    /**
     * @return whether this method can extract options from the given object.
     */
    boolean canAcceptOptions(Object opts) {
        for (Class<?> c : acceptableOptions) {
            if (c.isInstance(opts)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return property-bytes to write in a Folder block
     */
    byte[] getOptionsAsProperties(Object options) {
        return NONE;
    }

    /**
     * @return a stream that reads from in using the configured coder and password.
     */
    abstract InputStream decode(final InputStream in, final Coder coder, byte[] password) throws IOException;

    /**
     * If the option represents a number, return its integer
     * value, otherwise return the given default value.
     */
    protected static int numberOptionOrDefault(Object options, int defaultValue) {
        return options instanceof Number ? ((Number) options).intValue() : defaultValue;
    }
}
