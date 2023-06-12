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
package org.apache.commons.compress.archivers.zip;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * List of known compression methods
 *
 * Many of these methods are currently not supported by commons compress
 *
 * @since 1.5
 */
public enum ZipMethod {

    /**
     * UnShrinking.
     * dynamic Lempel-Ziv-Welch-Algorithm
     *
     * @see <a href="http://www.pkware.com/documents/casestudies/APPNOTE.TXT">Explanation of fields: compression
     *      method: (2 bytes)</a>
     */
    UNSHRINKING(1),
    /**
     * Imploding.
     *
     * @see <a href="http://www.pkware.com/documents/casestudies/APPNOTE.TXT">Explanation of fields: compression
     *      method: (2 bytes)</a>
     */
    IMPLODING(6);

    private final int code;

    private static final Map<Integer, ZipMethod> codeToEnum;

    static {
        Map<Integer, ZipMethod> cte = new HashMap<Integer, ZipMethod>();
        for (ZipMethod method : values()) {
            cte.put(Integer.valueOf(method.getCode()), method);
        }
        codeToEnum = Collections.unmodifiableMap(cte);
    }

    /**
     * private constructor for enum style class.
     */
    ZipMethod(int code) {
        this.code = code;
    }

    /**
     * the code of the compression method.
     *
     * @see ZipArchiveEntry#getMethod()
     *
     * @return an integer code for the method
     */
    public int getCode() {
        return code;
    }

    /**
     * returns the {@link ZipMethod} for the given code or null if the
     * method is not known.
     */
    public static ZipMethod getMethodByCode(int code) {
        return codeToEnum.get(Integer.valueOf(code));
    }
}
