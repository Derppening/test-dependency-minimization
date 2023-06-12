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
 *   Igor Bukanov, igor@fastmail.fm
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

import java.lang.reflect.Method;

/**
 * Collection of utilities
 */
public class Kit {

    /**
     * Reflection of Throwable.initCause(Throwable) from JDK 1.4
     * or nul if it is not available.
     */
    private static Method Throwable_initCause = null;

    static {
        // Are we running on a JDK 1.4 or later system?
        try {
            Class<?> ThrowableClass = Kit.classOrNull("java.lang.Throwable");
            Class<?>[] signature = { ThrowableClass };
            Throwable_initCause = ThrowableClass.getMethod("initCause", signature);
        } catch (Exception ex) {
            // Assume any exceptions means the method does not exist.
        }
    }

    public static Class<?> classOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
        } catch (SecurityException ex) {
        } catch (LinkageError ex) {
        } catch (IllegalArgumentException e) {
            // Can be thrown if name has characters that a class name
            // can not contain
        }
        return null;
    }

    /**
     * Throws RuntimeException to indicate failed assertion.
     * The function never returns and its return type is RuntimeException
     * only to be able to write <tt>throw Kit.codeBug()</tt> if plain
     * <tt>Kit.codeBug()</tt> triggers unreachable code error.
     */
    public static RuntimeException codeBug() throws RuntimeException {
        RuntimeException ex = new IllegalStateException("FAILED ASSERTION");
        // Print stack trace ASAP
        ex.printStackTrace(System.err);
        throw ex;
    }
}
