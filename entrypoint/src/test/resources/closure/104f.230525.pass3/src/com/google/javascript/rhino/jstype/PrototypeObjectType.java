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

import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.JSDocInfo;
import java.io.Serializable;
import java.util.Map;

/**
 * The object type represents instances of JavaScript objects such as
 * {@code Object}, {@code Date}, {@code Function}.<p>
 *
 * Objects in JavaScript are unordered collections of properties.
 * Each property consists of a name, a value and a set of attributes.<p>
 *
 * Each instance has an implicit prototype property ({@code [[Prototype]]})
 * pointing to an object instance, which itself has an implicit property, thus
 * forming a chain.<p>
 *
 * A class begins life with no name.  Later, a name may be provided once it
 * can be inferred.  Note that the name in this case is strictly for
 * debugging purposes.  Looking up type name references goes through the
 * {@link JSTypeRegistry}.<p>
 */
class PrototypeObjectType extends ObjectType {

    private static final long serialVersionUID = 1L;

    private final String className;

    private final Map<String, Property> properties;

    private ObjectType implicitPrototype;

    private final boolean nativeType;

    /**
     * Creates an object type.
     *
     * @param className the name of the class.  May be {@code null} to
     *        denote an anonymous class.
     *
     * @param implicitPrototype the implicit prototype
     *        (a.k.a. {@code [[Prototype]]}) as defined by ECMA-262. If the
     *        implicit prototype is {@code null} the implicit prototype will be
     *        set to the {@link JSTypeNative#OBJECT_TYPE}.
     */
    PrototypeObjectType(JSTypeRegistry registry, String className, ObjectType implicitPrototype) {
        this(registry, className, implicitPrototype, false);
    }

    /**
     * Creates an object type, allowing specification of the implicit prototype
     * when creating native objects.
     */
    PrototypeObjectType(JSTypeRegistry registry, String className, ObjectType implicitPrototype, boolean nativeType) {
        super(registry);
        this.properties = Maps.newHashMap();
        this.className = className;
        this.nativeType = nativeType;
        if (nativeType) {
            this.implicitPrototype = implicitPrototype;
        } else if (implicitPrototype == null) {
            this.implicitPrototype = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
        } else {
            this.implicitPrototype = implicitPrototype;
        }
    }

    /**
     * Gets the number of properties of this object.
     */
    @Override
    public int getPropertiesCount() {
        ObjectType implicitPrototype = getImplicitPrototype();
        if (implicitPrototype == null) {
            return this.properties.size();
        }
        int localCount = 0;
        for (String property : properties.keySet()) {
            if (!implicitPrototype.hasProperty(property)) {
                localCount++;
            }
        }
        return implicitPrototype.getPropertiesCount() + localCount;
    }

    @Override
    public boolean hasProperty(String propertyName) {
        if (properties.get(propertyName) != null) {
            return true;
        }
        ObjectType implicitPrototype = getImplicitPrototype();
        if (implicitPrototype != null) {
            return implicitPrototype.hasProperty(propertyName);
        }
        return false;
    }

    @Override
    public boolean hasOwnProperty(String propertyName) {
        return properties.get(propertyName) != null;
    }

    @Override
    public boolean isPropertyTypeDeclared(String property) {
        Property p = properties.get(property);
        if (p == null) {
            ObjectType implicitPrototype = getImplicitPrototype();
            if (implicitPrototype != null) {
                return implicitPrototype.isPropertyTypeDeclared(property);
            }
            // property does not exist
            return false;
        }
        return !p.inferred;
    }

    @Override
    public JSType getPropertyType(String propertyName) {
        Property p = properties.get(propertyName);
        if (p != null) {
            return p.type;
        }
        ObjectType implicitPrototype = getImplicitPrototype();
        if (implicitPrototype != null) {
            return implicitPrototype.getPropertyType(propertyName);
        }
        return getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }

    @Override
    boolean defineProperty(String name, JSType type, boolean inferred, boolean inExterns) {
        if (hasOwnDeclaredProperty(name)) {
            return false;
        }
        properties.put(name, new Property(type, inferred, inExterns));
        return true;
    }

    @Override
    public String toString() {
        return getReferenceName();
    }

    @Override
    public FunctionType getConstructor() {
        return null;
    }

    @Override
    public ObjectType getImplicitPrototype() {
        return implicitPrototype;
    }

    /**
     * This should only be reset on the FunctionPrototypeType, only to fix an
     * incorrectly established prototype chain due to the user having a mismatch
     * in super class declaration, and only before properties on that type are
     * processed.
     */
    void setImplicitPrototype(ObjectType implicitPrototype) {
        checkState(!hasCachedValues());
        this.implicitPrototype = implicitPrototype;
    }

    @Override
    public String getReferenceName() {
        if (className != null) {
            return className;
        } else {
            return "{...}";
        }
    }

    public boolean hasReferenceName() {
        return className != null;
    }

    @Override
    public boolean isSubtype(JSType that) {
        if (JSType.isSubtype(this, that)) {
            return true;
        }
        // Union types
        if (that instanceof UnionType) {
            // The static {@code JSType.isSubtype} check already decomposed
            // union types, so we don't need to check those again.
            return false;
        }
        // record types
        if (that instanceof RecordType) {
            return RecordType.isSubtype(this, (RecordType) that);
        }
        // Interfaces
        // Find all the interfaces implemented by this class and compare each one
        // to the interface instance.
        ObjectType thatObj = that.toObjectType();
        ObjectType thatCtor = thatObj == null ? null : thatObj.getConstructor();
        if (thatCtor != null && thatCtor.isInterface()) {
            Iterable<ObjectType> thisInterfaces = getCtorImplementedInterfaces();
            for (ObjectType thisInterface : thisInterfaces) {
                if (thisInterface.isSubtype(that)) {
                    return true;
                }
            }
        }
        // other prototype based objects
        if (that != null) {
            if (isUnknownType() || implicitPrototypeChainIsUnknown()) {
                // If unsure, say 'yes', to avoid spurious warnings.
                // TODO(user): resolve the prototype chain completely in all cases,
                // to avoid guessing.
                return true;
            }
            return this.isImplicitPrototype(thatObj);
        }
        return false;
    }

    private boolean implicitPrototypeChainIsUnknown() {
        ObjectType p = getImplicitPrototype();
        while (p != null) {
            if (p.isUnknownType()) {
                return true;
            }
            p = p.getImplicitPrototype();
        }
        return false;
    }

    private static final class Property implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Property's type.
         */
        private JSType type;

        /**
         * Whether the property's type is inferred.
         */
        private final boolean inferred;

        /**
         * Whether the property is defined in the externs.
         */
        private final boolean inExterns;

        /**
         *  The JSDocInfo for this property.
         */
        private JSDocInfo docInfo = null;

        private Property(JSType type, boolean inferred, boolean inExterns) {
            this.type = type;
            this.inferred = inferred;
            this.inExterns = inExterns;
        }
    }

    @Override
    public boolean hasCachedValues() {
        return super.hasCachedValues();
    }

    /**
     * Whether this is a built-in object.
     */
    @Override
    public boolean isNativeObjectType() {
        return nativeType;
    }
}
