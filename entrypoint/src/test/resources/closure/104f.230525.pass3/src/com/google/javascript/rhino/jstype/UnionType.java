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

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The {@code UnionType} implements a common JavaScript idiom in which the
 * code is specifically designed to work with multiple input types.  Because
 * JavaScript always knows the runtime type of an object value, this is safer
 * than a C union.<p>
 *
 * For instance, values of the union type {@code (String,boolean)} can be of
 * type {@code String} or of type {@code boolean}. The commutativity of the
 * statement is captured by making {@code (String,boolean)} and
 * {@code (boolean,String)} equal.<p>
 *
 * The implementation of this class prevents the creation of nested
 * unions.<p>
 */
public class UnionType extends JSType {

    private static final long serialVersionUID = 1L;

    Set<JSType> alternates;

    /**
     * Creates a union type.
     *
     * @param alternates the alternates of the union
     */
    UnionType(JSTypeRegistry registry, Set<JSType> alternates) {
        super(registry);
        this.alternates = alternates;
    }

    /**
     * Gets the alternate types of this union type.
     * @return The alternate types of this union type. The returned set is
     *     immutable.
     */
    public Iterable<JSType> getAlternates() {
        return alternates;
    }

    @Override
    public boolean isUnknownType() {
        for (JSType t : alternates) {
            if (t.isUnknownType()) {
                return true;
            }
        }
        return false;
    }

    JSType meet(JSType that) {
        UnionTypeBuilder builder = new UnionTypeBuilder(registry);
        for (JSType alternate : alternates) {
            if (alternate.isSubtype(that)) {
                builder.addAlternate(alternate);
            }
        }
        if (that instanceof UnionType) {
            for (JSType otherAlternate : ((UnionType) that).alternates) {
                if (otherAlternate.isSubtype(this)) {
                    builder.addAlternate(otherAlternate);
                }
            }
        } else if (that.isSubtype(this)) {
            builder.addAlternate(that);
        }
        JSType result = builder.build();
        if (!result.isNoType()) {
            return result;
        } else if (this.isObject() && that.isObject()) {
            return getNativeType(JSTypeNative.NO_OBJECT_TYPE);
        } else {
            return getNativeType(JSTypeNative.NO_TYPE);
        }
    }

    /**
     * Two union types are equal if they have the same number of alternates
     * and all alternates are equal.
     */
    @Override
    public boolean equals(Object object) {
        if (object instanceof UnionType) {
            UnionType that = (UnionType) object;
            return alternates.equals(that.alternates);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return alternates.hashCode();
    }

    @Override
    public boolean isUnionType() {
        return true;
    }

    @Override
    public boolean isObject() {
        for (JSType alternate : alternates) {
            if (!alternate.isObject()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a more restricted union type than {@code this} one, in which all
     * subtypes of {@code type} have been removed.<p>
     *
     * Examples:
     * <ul>
     * <li>{@code (number,string)} restricted by {@code number} is
     *     {@code string}</li>
     * <li>{@code (null, EvalError, URIError)} restricted by
     *     {@code Error} is {@code null}</li>
     * </ul>
     *
     * @param type the supertype of the types to remove from this union type
     */
    public JSType getRestrictedUnion(JSType type) {
        UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
        for (JSType t : alternates) {
            if (t.isUnknownType() || !t.isSubtype(type)) {
                restricted.addAlternate(t);
            }
        }
        return restricted.build();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        boolean firstAlternate = true;
        result.append("(");
        SortedSet<JSType> sorted = new TreeSet<JSType>(ALPHA);
        sorted.addAll(alternates);
        for (JSType t : sorted) {
            if (!firstAlternate) {
                result.append("|");
            }
            result.append(t.toString());
            firstAlternate = false;
        }
        result.append(")");
        return result.toString();
    }

    @Override
    public boolean isSubtype(JSType that) {
        for (JSType element : alternates) {
            if (!element.isSubtype(that)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public <T> T visit(Visitor<T> visitor) {
        return visitor.caseUnionType(this);
    }
}
