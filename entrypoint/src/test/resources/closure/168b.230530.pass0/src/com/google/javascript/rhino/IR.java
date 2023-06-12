/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   John Lenz
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */
package com.google.javascript.rhino;

/**
 * An AST construction helper class
 * @author johnlenz@google.com (John Lenz)
 */
public class IR {

    private IR() {
        throw new AssertionError("This method should not be reached! Signature: IR()");
    }

    public static Node empty() {
        return new Node(Token.EMPTY);
    }

    public static Node paramList() {
        return new Node(Token.PARAM_LIST);
    }

    public static Node block() {
        Node block = new Node(Token.BLOCK);
        return block;
    }

    public static Node script() {
        // TODO(johnlenz): finish setting up the SCRIPT node
        Node block = new Node(Token.SCRIPT);
        return block;
    }

    //
    public static Node name(String name) {
        return Node.newString(Token.NAME, name);
    }

    // TODO(johnlenz): the rest of the ops
    // TODO(johnlenz): quoted props
    public static Node string(String s) {
        return Node.newString(s);
    }

    public static Node number(double d) {
        return Node.newNumber(d);
    }
    // helper methods
    // NOTE: some nodes are neither statements nor expression nodes:
    //   SCRIPT, LABEL_NAME, PARAM_LIST, CASE, DEFAULT_CASE, CATCH
    //   GETTER_DEF, SETTER_DEF
}
