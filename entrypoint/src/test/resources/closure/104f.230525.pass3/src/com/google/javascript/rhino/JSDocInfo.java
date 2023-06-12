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
 *   Bob Jervis
 *   Google Inc.
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

import java.io.Serializable;

/**
 * <p>JSDoc information describing JavaScript code. JSDoc is represented as a
 * unified object with fields for each JSDoc annotation, even though some
 * combinations are incorrect. For instance, if a JSDoc describes an enum,
 * it cannot have information about a return type. This implementation
 * takes advantage of such incompatibilities to reuse fields for multiple
 * purposes, reducing memory consumption.</p>
 *
 * <p>Constructing {@link JSDocInfo} objects is simplified by
 * {@link JSDocInfoBuilder} which provides early incompatibility detection.</p>
 */
public final class JSDocInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    // We use a bit map to represent whether or not the JSDoc contains
    // one of the "boolean" annotation types (annotations like @constructor,
    // for which the presence of the annotation alone is significant).
    // Mask all the boolean annotation types
    private static final int MASK_FLAGS = 0x3FFFFFFF;

    // @const
    private static final int MASK_CONSTANT = 0x00000001;

    // @constructor
    private static final int MASK_CONSTRUCTOR = 0x00000002;

    // @define
    private static final int MASK_DEFINE = 0x00000004;

    // @hidden
    private static final int MASK_HIDDEN = 0x00000008;

    // @preserveTry
    private static final int MASK_PRESERVETRY = 0x00000010;

    // @notypecheck
    private static final int MASK_NOCHECK = 0x00000020;

    // @override
    private static final int MASK_OVERRIDE = 0x00000040;

    // @noalias
    private static final int MASK_NOALIAS = 0x00000080;

    // @deprecated
    private static final int MASK_DEPRECATED = 0x00000100;

    // @interface
    private static final int MASK_INTERFACE = 0x00000200;

    // @export
    private static final int MASK_EXPORT = 0x00000400;

    // @noshadow
    private static final int MASK_NOSHADOW = 0x00000800;

    // @fileoverview
    private static final int MASK_FILEOVERVIEW = 0x00001000;

    // @implicitCast
    private static final int MASK_IMPLICITCAST = 0x00002000;

    // @nosideeffects
    private static final int MASK_NOSIDEEFFECTS = 0x00004000;

    // @externs
    private static final int MASK_EXTERNS = 0x00008000;

    // @javadispath
    private static final int MASK_JAVADISPATCH = 0x00010000;

    // 3 bit type field stored in the top 3 bits of the most significant
    // nibble.
    // 1110...
    private static final int MASK_TYPEFIELD = 0xE0000000;

    // 0010...
    private static final int TYPEFIELD_TYPE = 0x20000000;

    // 0100...
    private static final int TYPEFIELD_RETURN = 0x40000000;

    // 0110...
    private static final int TYPEFIELD_ENUM = 0x60000000;

    // 1000...
    private static final int TYPEFIELD_TYPEDEF = 0x80000000;

    // Visible for testing.
    public JSDocInfo() {
        throw new AssertionError("This method should not be reached! Signature: JSDocInfo()");
    }
    /**
     * License directives can appear in multiple comments, and always
     * apply to the entire file.  Break protection and allow outsiders to
     * update the license string so that we can attach the license text even
     * when the JSDocInfo has been created and tagged with other information.
     * @param license String containing new license text.
     */
}
