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

import java.io.Serializable;
import java.util.Comparator;

/**
 * Represents JavaScript value types.<p>
 *
 * Types are split into two separate families: value types and object types.
 *
 * A special {@link UnknownType} exists to represent a wildcard type on which
 * no information can be gathered. In particular, it can assign to everyone,
 * is a subtype of everyone (and everyone is a subtype of it).<p>
 *
 * If you remove the {@link UnknownType}, the set of types in the type system
 * forms a lattice with the {@link #isSubtype} relation defining the partial
 * order of types. All types are united at the top of the lattice by the
 * {@link AllType} and at the bottom by the {@link NoType}.<p>
 */
public abstract class JSType implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String UNKNOWN_NAME = "Unknown class name";

    public static final String NOT_A_CLASS = "Not declared as a constructor";

    public static final String NOT_A_TYPE = "Not declared as a type name";

    public static final String EMPTY_TYPE_COMPONENT = "Named type with empty name component";

    /**
     * Total ordering on types based on their textual representation.
     * This is used to have a deterministic output of the toString
     * method of the union type since this output is used in tests.
     */
    static final Comparator<JSType> ALPHA = new Comparator<JSType>() {

        public int compare(JSType t1, JSType t2) {
            return t1.toString().compareTo(t2.toString());
        }
    };

    // A flag set on enum definition tree nodes
    public static final int ENUMDECL = 1;

    public static final int NOT_ENUMDECL = 0;

    final JSTypeRegistry registry;

    JSType(JSTypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Utility method for less verbose code.
     */
    JSType getNativeType(JSTypeNative typeId) {
        return registry.getNativeType(typeId);
    }

    public boolean isNoType() {
        return false;
    }

    public boolean isNoObjectType() {
        return false;
    }

    public final boolean isEmptyType() {
        return isNoType() || isNoObjectType();
    }

    public boolean isAllType() {
        return false;
    }

    public boolean isUnknownType() {
        return false;
    }

    public boolean isCheckedUnknownType() {
        return false;
    }

    public boolean isUnionType() {
        return false;
    }

    public boolean isFunctionType() {
        return false;
    }

    public boolean isRecordType() {
        return false;
    }

    /**
     * Tests whether this type is an {@code Object}, or any subtype thereof.
     * @return {@code this &lt;: Object}
     */
    public boolean isObject() {
        return false;
    }

    /**
     * Whether this type is a nominal type (a named instance object or
     * a named enum).
     */
    public boolean isNominalType() {
        return false;
    }

    /**
     * Whether this type is a {@link FunctionType} that is an interface or a named
     * type that points to such a type.
     */
    public boolean isInterface() {
        return false;
    }

    /**
     * This method relies on the fact that for the base {@link JSType}, only one
     * instance of each sub-type will ever be created in a given registry, so
     * there is no need to verify members. If the object pointers are not
     * identical, then the type member must be different.
     */
    @Override
    public boolean equals(Object jsType) {
        if (jsType instanceof ProxyObjectType) {
            return jsType.equals(this);
        }
        return this == jsType;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Casts this to an ObjectType, or returns null if this is not an ObjectType.
     *
     * Does not change the underlying JS type. If you want to simulate JS
     * autoboxing or dereferencing, you should use autoboxesTo() or dereference().
     * Those methods may change the underlying JS type.
     */
    public ObjectType toObjectType() {
        return this instanceof ObjectType ? (ObjectType) this : null;
    }

    /**
     * Gets the greatest subtype of {@code this} and {@code that}.
     * The greatest subtype is the meet (&#8743;) or infimum of both types in the
     * type lattice.<p>
     * Examples
     * <ul>
     * <li>{@code Number &#8743; Any} = {@code Any}</li>
     * <li>{@code number &#8743; Object} = {@code Any}</li>
     * <li>{@code Number &#8743; Object} = {@code Number}</li>
     * </ul>
     * @return {@code this &#8744; that}
     */
    public JSType getGreatestSubtype(JSType that) {
        if (that.isRecordType()) {
            // Record types have their own implementation of getGreatestSubtype.
            return that.getGreatestSubtype(this);
        }
        return getGreatestSubtype(this, that);
    }

    /**
     * A generic implementation meant to be used as a helper for common
     * getGreatestSubtype implementations.
     */
    static JSType getGreatestSubtype(JSType thisType, JSType thatType) {
        if (thatType.isEmptyType() || thatType.isAllType()) {
            // Defer to the implementations of the end lattice elements when
            // possible.
            return thatType.getGreatestSubtype(thisType);
        } else if (thisType.isUnknownType() || thatType.isUnknownType()) {
            // The greatest subtype with any unknown type is the universal
            // unknown type, unless the two types are equal.
            return thisType.equals(thatType) ? thisType : thisType.getNativeType(JSTypeNative.UNKNOWN_TYPE);
        } else if (thisType.isSubtype(thatType)) {
            return thisType;
        } else if (thatType.isSubtype(thisType)) {
            return thatType;
        } else if (thisType.isUnionType()) {
            return ((UnionType) thisType).meet(thatType);
        } else if (thatType.isUnionType()) {
            return ((UnionType) thatType).meet(thisType);
        } else if (thisType.isObject() && thatType.isObject()) {
            return thisType.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
        }
        return thisType.getNativeType(JSTypeNative.NO_TYPE);
    }

    /**
     * Checks whether {@code this} is a subtype of {@code that}.<p>
     *
     * Subtyping rules:
     * <ul>
     * <li>(unknown) &mdash; every type is a subtype of the Unknown type.</li>
     * <li>(no) &mdash; the No type is a subtype of every type.</li>
     * <li>(no-object) &mdash; the NoObject type is a subtype of every object
     * type (i.e. subtypes of the Object type).</li>
     * <li>(ref) &mdash; a type is a subtype of itself.</li>
     * <li>(union-l) &mdash; A union type is a subtype of a type U if all the
     * union type's constituents are a subtype of U. Formally<br>
     * {@code (T<sub>1</sub>, &hellip;, T<sub>n</sub>) &lt;: U} if and only
     * {@code T<sub>k</sub> &lt;: U} for all {@code k &isin; 1..n}.</li>
     * <li>(union-r) &mdash; A type U is a subtype of a union type if it is a
     * subtype of one of the union type's constituents. Formally<br>
     * {@code U &lt;: (T<sub>1</sub>, &hellip;, T<sub>n</sub>)} if and only
     * if {@code U &lt;: T<sub>k</sub>} for some index {@code k}.</li>
     * <li>(objects) &mdash; an Object {@code O<sub>1</sub>} is a subtype
     * of an object {@code O<sub>2</sub>} if it has more properties
     * than {@code O<sub>2</sub>} and all common properties are
     * pairwise subtypes.</li>
     * </ul>
     *
     * @return {@code this &lt;: that}
     */
    public abstract boolean isSubtype(JSType that);

    /**
     * A generic implementation meant to be used as a helper for common subtyping
     * cases.
     */
    static boolean isSubtype(JSType thisType, JSType thatType) {
        // unknown
        if (thatType.isUnknownType()) {
            return true;
        }
        // equality
        if (thisType.equals(thatType)) {
            return true;
        }
        // all type
        if (thatType.isAllType()) {
            return true;
        }
        // unions
        if (thatType instanceof UnionType) {
            UnionType union = (UnionType) thatType;
            for (JSType element : union.alternates) {
                if (thisType.isSubtype(element)) {
                    return true;
                }
            }
        }
        // named types
        if (thatType instanceof NamedType) {
            return thisType.isSubtype(((NamedType) thatType).referencedType);
        }
        return false;
    }

    /**
     * Visit this type with the given visitor.
     * @see com.google.javascript.rhino.jstype.Visitor
     * @return the value returned by the visitor
     */
    public abstract <T> T visit(Visitor<T> visitor);
}
