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
package com.google.javascript.rhino.jstype;

import com.google.common.base.Preconditions;

/**
 * A {@code NamedType} is a named reference to some other type.  This provides
 * a convenient mechanism for implementing forward references to types; a
 * {@code NamedType} can be used as a placeholder until its reference is
 * resolved.  It is also useful for representing type names in jsdoc type
 * annotations, some of which may never be resolved (as they may refer to
 * types in host systems not yet supported by JSCompiler, such as the JVM.)<p>
 *
 * An important distinction: {@code NamedType} is a type name reference,
 * whereas {@link ObjectType} is a named type object, such as an Enum name.
 * The Enum itself is typically used only in a dot operator to name one of its
 * constants, or in a declaration, where its name will appear in a
 * NamedType.<p>
 *
 * A {@code NamedType} is not currently a full-fledged typedef, because it
 * cannot resolve to any JavaScript type.  It can only resolve to a named
 * {@link JSTypeRegistry} type, or to {@link FunctionType} or
 * {@link EnumType}.<p>
 *
 * If full typedefs are to be supported, then each method on each type class
 * needs to be reviewed to make sure that everything works correctly through
 * typedefs.  Alternatively, we would need to walk through the parse tree and
 * unroll each reference to a {@code NamedType} to its resolved type before
 * applying the rest of the analysis.<p>
 *
 * TODO(user): Revisit all of this logic.<p>
 *
 * The existing typing logic is hacky.  Unresolved types should get processed
 * in a more consistent way, but with the Rhino merge coming, there will be
 * much that has to be changed.<p>
 */
public class NamedType extends ProxyObjectType {

    private static final long serialVersionUID = 1L;

    private final String reference;

    private final String sourceName;

    private final int lineno;

    private final int charno;

    /**
     * If true, don't warn about unresolveable type names.
     *
     * NOTE(nicksantos): A lot of third-party code doesn't use our type syntax.
     * They have code like
     * {@code @return} the bus.
     * and they clearly don't mean that "the" is a type. In these cases, we're
     * forgiving and try to guess whether or not "the" is a type when it's not
     * clear.
     */
    private boolean forgiving = false;

    /**
     * Create a named type based on the reference.
     */
    public NamedType(JSTypeRegistry registry, String reference, String sourceName, int lineno, int charno) {
        super(registry, registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE));
        Preconditions.checkNotNull(reference);
        this.reference = reference;
        this.sourceName = sourceName;
        this.lineno = lineno;
        this.charno = charno;
    }

    @Override
    public String getReferenceName() {
        return reference;
    }

    @Override
    public String toString() {
        return reference;
    }

    @Override
    public boolean isNominalType() {
        return true;
    }

    /**
     * Two named types are equal if they are the same {@code ObjectType} object.
     * This is complicated by the fact that equals is sometimes called before we
     * have a chance to resolve the type names.
     *
     * @return {@code true} iff {@code that} == {@code this} or {@code that}
     *         is a {@link NamedType} whose reference is the same as ours,
     *         or {@code that} is the type we reference.
     */
    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that instanceof JSType) {
            ObjectType objType = ObjectType.cast((JSType) that);
            if (objType != null) {
                return objType.isNominalType() && reference.equals(objType.getReferenceName());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return reference.hashCode();
    }
    // Warns about this type being unresolved iff it's not a forward-declared
}
