/*
 * Copyright 2006 The Closure Compiler Authors.
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A representation of a translatable message in JavaScript source code.
 *
 * <p>Instances are created using a {@link JsMessage.Builder},
 * like this:
 * <pre>
 * JsMessage m = new JsMessage.Builder(key)
 *     .appendPart("Hi ")
 *     .appendPlaceholderReference("firstName")
 *     .appendPart("!")
 *     .setDesc("A welcome message")
 *     .build();
 * </pre>
 */
public class JsMessage {

    /**
     * Message style that could be used for JS code parsing.
     * The enum order is from most relaxed to most restricted.
     */
    public enum Style {

        // Any legacy code is prohibited
        CLOSURE
    }

    private static final String MESSAGE_REPRESENTATION_FORMAT = "{$%s}";

    private final String key;

    private final String id;

    private final List<CharSequence> parts;

    private final Set<String> placeholders;

    private final String desc;

    private final boolean hidden;

    private final String meaning;

    private final String sourceName;

    private final boolean isAnonymous;

    private final boolean isExternal;

    /**
     * Creates an instance. Client code should use a {@link JsMessage.Builder}.
     *
     * @param key a key that should identify this message in sources; typically
     *     it is the message's name (e.g. {@code "MSG_HELLO"}).
     * @param id an id that *uniquely* identifies the message in the bundle.
     *     It could be either the message name or id generated from the message
     *     content.
     * @param meaning The user-specified meaning of the message. May be null if
     *     the user did not specify an explicit meaning.
     */
    private JsMessage(String sourceName, String key, boolean isAnonymous, boolean isExternal, String id, List<CharSequence> parts, Set<String> placeholders, String desc, boolean hidden, String meaning) {
        Preconditions.checkState(key != null);
        Preconditions.checkState(id != null);
        this.key = key;
        this.id = id;
        this.parts = Collections.unmodifiableList(parts);
        this.placeholders = Collections.unmodifiableSet(placeholders);
        this.desc = desc;
        this.hidden = hidden;
        this.meaning = meaning;
        this.sourceName = sourceName;
        this.isAnonymous = isAnonymous;
        this.isExternal = isExternal;
    }

    public interface IdGenerator {
    }
}
