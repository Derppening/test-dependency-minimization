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

import java.util.HashMap;
import java.util.Map;

/**
 * A record (structural) type.
 *
 * Subtyping: The subtyping of a record type is defined via structural
 * comparison of a record type's properties. For example, a record
 * type of the form { a : TYPE_1 } is a supertype of a record type
 * of the form { b : TYPE_2, a : TYPE_1 } because B can be assigned to
 * A and matches all constraints. Similarly, a defined type can be assigned
 * to a record type so long as that defined type matches all property
 * constraints of the record type. A record type of the form { a : A, b : B }
 * can be assigned to a record of type { a : A }.
 */
public class RecordType extends PrototypeObjectType {

    private static final long serialVersionUID = 1L;

    private Map<String, JSType> properties = new HashMap<String, JSType>();

    /**
     * Creates a record type.
     *
     * @param registry The type registry under which this type lives.
     * @param properties A map of all the properties of this record type.
     */
    RecordType(JSTypeRegistry registry, Map<String, JSType> properties) {
        super(registry, null, null);
        throw new AssertionError("This method should not be reached! Signature: RecordType(JSTypeRegistry, Map)");
    }

    @Override
    public boolean equals(Object other) {
        throw new AssertionError("This method should not be reached! Signature: equals(Object)");
    }

    @Override
    public ObjectType getImplicitPrototype() {
        throw new AssertionError("This method should not be reached! Signature: getImplicitPrototype()");
    }

    @Override
    boolean defineProperty(String propertyName, JSType type, boolean inferred, boolean inExterns) {
        throw new AssertionError("This method should not be reached! Signature: defineProperty(String, JSType, boolean, boolean)");
    }

    @Override
    public JSType getGreatestSubtype(JSType that) {
        throw new AssertionError("This method should not be reached! Signature: getGreatestSubtype(JSType)");
    }

    @Override
    public boolean isRecordType() {
        throw new AssertionError("This method should not be reached! Signature: isRecordType()");
    }

    @Override
    public boolean isSubtype(JSType that) {
        throw new AssertionError("This method should not be reached! Signature: isSubtype(JSType)");
    }

    /**
     * Determines if typeA is a subtype of typeB
     */
    static boolean isSubtype(ObjectType typeA, RecordType typeB) {
        // typeA is a subtype of record type typeB iff:
        // 1) typeA has all the properties declared in typeB.
        // 2) And for each property of typeB,
        //    2a) if the property of typeA is declared, it must be equal
        //        to the type of the property of typeB,
        //    2b) otherwise, it must be a subtype of the property of typeB.
        //
        // To figure out why this is true, consider the following pseudo-code:
        // /** @type {{a: (Object,null)}} */ var x;
        // /** @type {{a: !Object}} */ var y;
        // var z = {a: {}};
        // x.a = null;
        //
        // y cannot be assigned to x, because line 4 would violate y's declared
        // properties. But z can be assigned to x. Even though z and y are the
        // same type, the properties of z are inferred--and so an assignment
        // to the property of z would not violate any restrictions on it.
        for (String property : typeB.properties.keySet()) {
            if (!typeA.hasProperty(property)) {
                return false;
            }
            JSType propA = typeA.getPropertyType(property);
            JSType propB = typeB.getPropertyType(property);
            if (!propA.isUnknownType() && !propB.isUnknownType()) {
                if (typeA.isPropertyTypeDeclared(property)) {
                    if (!propA.equals(propB)) {
                        return false;
                    }
                } else {
                    if (!propA.isSubtype(propB)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        throw new AssertionError("This method should not be reached! Signature: toString()");
    }
}
