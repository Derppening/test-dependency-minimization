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
 * Portions created by the Initial Developer are Copyright (C) 1997-2000
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Patrick Beard
 *   Norris Boyd
 *   Igor Bukanov
 *   Ethan Hugg
 *   Roger Lawrence
 *   Terry Lucas
 *   Frank Mitchell
 *   Milen Nankov
 *   Andrew Wason
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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * This is the class that implements the run-time.
 */
public class ScriptRuntime {

    /**
     * No instances should be created.
     */
    protected ScriptRuntime() {
        throw new AssertionError("This method should not be reached! Signature: ScriptRuntime()");
    }

    // Can not use Double.NaN defined as 0.0d / 0.0 as under the Microsoft VM,
    // versions 2.01 and 3.0P1, that causes some uses (returns at least) of
    // Double.NaN to be converted to 1.0.
    // So we use ScriptRuntime.NaN instead of Double.NaN.
    public static final double NaN = Double.longBitsToDouble(0x7ff8000000000000L);

    // A similar problem exists for negative zero.
    public static final double negativeZero = Double.longBitsToDouble(0x8000000000000000L);

    // ------------------
    // Statements
    // ------------------
    public static String getMessage0(String messageId) {
        return getMessage(messageId, null);
    }

    public static String getMessage1(String messageId, Object arg1) {
        Object[] arguments = { arg1 };
        return getMessage(messageId, arguments);
    }

    /* OPT there's a noticeable delay for the first error!  Maybe it'd
     * make sense to use a ListResourceBundle instead of a properties
     * file to avoid (synchronized) text parsing.
     */
    public static String getMessage(String messageId, Object[] arguments) {
        final String defaultResource = "rhino_ast.java.com.google.javascript.rhino.Messages";
        Locale locale = Locale.getDefault();
        // ResourceBundle does caching.
        ResourceBundle rb = ResourceBundle.getBundle(defaultResource, locale);
        String formatString;
        try {
            formatString = rb.getString(messageId);
        } catch (java.util.MissingResourceException mre) {
            throw new RuntimeException("no message resource found for message property " + messageId);
        }
        /*
         * It's OK to format the string, even if 'arguments' is null;
         * we need to format it anyway, to make double ''s collapse to
         * single 's.
         */
        // TODO: MessageFormat is not available on pJava
        MessageFormat formatter = new MessageFormat(formatString);
        return formatter.format(arguments);
    }
}
