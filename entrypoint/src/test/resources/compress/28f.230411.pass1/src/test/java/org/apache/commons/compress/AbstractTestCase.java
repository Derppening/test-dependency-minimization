/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import junit.framework.TestCase;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;

public abstract class AbstractTestCase extends TestCase {

    protected File dir;

    protected File resultDir;

    // used to delete the archive in tearDown
    private File archive;

    protected ArchiveStreamFactory factory = new ArchiveStreamFactory();

    public AbstractTestCase() {
        throw new AssertionError("This method should not be reached! Signature: AbstractTestCase()");
    }

    public AbstractTestCase(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        dir = mkdir("dir");
        resultDir = mkdir("dir-result");
        archive = null;
    }

    public static File mkdir(String name) throws IOException {
        File f = File.createTempFile(name, "");
        f.delete();
        f.mkdir();
        return f;
    }

    @Override
    protected void tearDown() throws Exception {
        rmdir(dir);
        rmdir(resultDir);
        dir = resultDir = null;
        if (!tryHardToDelete(archive)) {
            // Note: this exception won't be shown if the test has already failed
            throw new Exception("Could not delete " + archive.getPath());
        }
    }

    public static void rmdir(File f) {
        String[] s = f.list();
        if (s != null) {
            for (String element : s) {
                final File file = new File(f, element);
                if (file.isDirectory()) {
                    rmdir(file);
                }
                boolean ok = tryHardToDelete(file);
                if (!ok && file.exists()) {
                    System.out.println("Failed to delete " + element + " in " + f.getPath());
                }
            }
        }
        // safer to delete and check
        tryHardToDelete(f);
        if (f.exists()) {
            throw new Error("Failed to delete " + f.getPath());
        }
    }

    private static final boolean ON_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).indexOf("windows") > -1;

    /**
     * Accommodate Windows bug encountered in both Sun and IBM JDKs.
     * Others possible. If the delete does not work, call System.gc(),
     * wait a little and try again.
     *
     * @return whether deletion was successful
     * @since Stolen from FileUtils in Ant 1.8.0
     */
    public static boolean tryHardToDelete(File f) {
        if (f != null && f.exists() && !f.delete()) {
            if (ON_WINDOWS) {
                System.gc();
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                // Ignore Exception
            }
            return f.delete();
        }
        return true;
    }
}
