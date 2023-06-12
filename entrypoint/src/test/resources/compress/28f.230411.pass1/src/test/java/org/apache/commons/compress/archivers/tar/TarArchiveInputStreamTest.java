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
package org.apache.commons.compress.archivers.tar;

import static org.apache.commons.compress.AbstractTestCase.mkdir;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Test;

public class TarArchiveInputStreamTest {

    @Test(expected = IOException.class)
    public void shouldThrowAnExceptionOnTruncatedEntries() throws Exception {
        File dir = mkdir("COMPRESS-279");
        TarArchiveInputStream is = getTestStream("/COMPRESS-279.tar");
        FileOutputStream out = null;
        try {
            TarArchiveEntry entry = is.getNextTarEntry();
            int count = 0;
            while (entry != null) {
                out = new FileOutputStream(new File(dir, String.valueOf(count)));
                IOUtils.copy(is, out);
                out.close();
                out = null;
                count++;
                entry = is.getNextTarEntry();
            }
        } finally {
            is.close();
            if (out != null) {
                out.close();
            }
        }
    }

    private TarArchiveInputStream getTestStream(String name) {
        return new TarArchiveInputStream(TarArchiveInputStreamTest.class.getResourceAsStream(name));
    }
}
