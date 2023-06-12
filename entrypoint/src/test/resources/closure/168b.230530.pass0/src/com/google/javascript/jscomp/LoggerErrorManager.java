/*
 * Copyright 2007 The Closure Compiler Authors.
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

import java.util.logging.Logger;

/**
 * An error manager that logs errors and warnings using a logger in addition to
 * collecting them in memory. Errors are logged at the SEVERE level and warnings
 * are logged at the WARNING level.
 */
public class LoggerErrorManager extends BasicErrorManager {

    private final MessageFormatter formatter;

    private final Logger logger;

    /**
     * Creates an instance.
     */
    public LoggerErrorManager(MessageFormatter formatter, Logger logger) {
        this.formatter = formatter;
        this.logger = logger;
    }
}
