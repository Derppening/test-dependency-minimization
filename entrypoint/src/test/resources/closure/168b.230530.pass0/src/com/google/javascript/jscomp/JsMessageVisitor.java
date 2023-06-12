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
package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.regex.*;

/**
 * Traverses across parsed tree and finds I18N messages. Then it passes it to
 * {@link JsMessageVisitor#processJsMessage(JsMessage, JsMessageDefinition)}.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
abstract class JsMessageVisitor extends AbstractPostOrderCallback implements CompilerPass {

    private static final String MSG_FUNCTION_NAME = "goog.getMsg";

    static final DiagnosticType MESSAGE_HAS_NO_DESCRIPTION = DiagnosticType.warning("JSC_MSG_HAS_NO_DESCRIPTION", "Message {0} has no description. Add @desc JsDoc tag.");

    static final DiagnosticType MESSAGE_HAS_NO_TEXT = DiagnosticType.warning("JSC_MSG_HAS_NO_TEXT", "Message value of {0} is just an empty string. " + "Empty messages are forbidden.");

    static final DiagnosticType MESSAGE_TREE_MALFORMED = DiagnosticType.error("JSC_MSG_TREE_MALFORMED", "Message parse tree malformed. {0}");

    static final DiagnosticType MESSAGE_HAS_NO_VALUE = DiagnosticType.error("JSC_MSG_HAS_NO_VALUE", "message node {0} has no value");

    static final DiagnosticType MESSAGE_DUPLICATE_KEY = DiagnosticType.error("JSC_MSG_KEY_DUPLICATED", "duplicate message variable name found for {0}, " + "initial definition {1}:{2}");

    static final DiagnosticType MESSAGE_NODE_IS_ORPHANED = DiagnosticType.warning("JSC_MSG_ORPHANED_NODE", MSG_FUNCTION_NAME + "() function could be used only with MSG_* property or variable");

    static final DiagnosticType MESSAGE_NOT_INITIALIZED_USING_NEW_SYNTAX = DiagnosticType.error("JSC_MSG_NOT_INITIALIZED_USING_NEW_SYNTAX", "message not initialized using " + MSG_FUNCTION_NAME);

    private static final String PH_JS_PREFIX = "{$";

    private static final String PH_JS_SUFFIX = "}";

    static final String MSG_PREFIX = "MSG_";

    /**
     * Pattern for unnamed messages.
     * <p>
     * All JS messages in JS code should have unique name but messages in
     * generated code (i.e. from soy template) could have duplicated message names.
     * Later we replace the message names with ids constructed as a hash of the
     * message content.
     * <p>
     * <link href="http://code.google.com/p/closure-templates/">
     * Soy</link> generates messages with names MSG_UNNAMED_<NUMBER> . This
     * pattern recognizes such messages.
     */
    private static final Pattern MSG_UNNAMED_PATTERN = Pattern.compile("MSG_UNNAMED_\\d+");

    private static final Pattern CAMELCASE_PATTERN = Pattern.compile("[a-z][a-zA-Z\\d]*[_\\d]*");

    static final String HIDDEN_DESC_PREFIX = "@hidden";

    // For old-style JS messages
    private static final String DESC_SUFFIX = "_HELP";

    private final boolean needToCheckDuplications;

    private final JsMessage.Style style;

    private final JsMessage.IdGenerator idGenerator;

    final AbstractCompiler compiler;

    private final CheckLevel checkLevel;

    /**
     * Creates JS message visitor.
     *
     * @param compiler the compiler instance
     * @param needToCheckDuplications whether to check duplicated messages in
     *        traversed
     * @param style style that should be used during parsing
     * @param idGenerator generator that used for creating unique ID for the
     *        message
     */
    JsMessageVisitor(AbstractCompiler compiler, boolean needToCheckDuplications, JsMessage.Style style, JsMessage.IdGenerator idGenerator) {
        this.compiler = compiler;
        this.needToCheckDuplications = needToCheckDuplications;
        this.style = style;
        this.idGenerator = idGenerator;
        checkLevel = (style == JsMessage.Style.CLOSURE) ? CheckLevel.ERROR : CheckLevel.WARNING;
        // TODO(anatol): add flag that decides whether to process UNNAMED messages.
        // Some projects would not want such functionality (unnamed) as they don't
        // use SOY templates.
    }

    @Override
    public void process(Node externs, Node root) {
        throw new AssertionError("This method should not be reached! Signature: process(Node, Node)");
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
        throw new AssertionError("This method should not be reached! Signature: visit(NodeTraversal, Node, Node)");
    }
}
