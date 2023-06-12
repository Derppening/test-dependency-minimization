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

import com.google.common.collect.ImmutableSet;

/**
 * Object type.
 *
 * In JavaScript, all object types have properties, and each of those
 * properties has a type. Property types may be DECLARED, INFERRED, or
 * UNKNOWN.
 *
 * DECLARED properties have an explicit type annotation, as in:
 * <code>
 * /xx @type {number} x/
 * Foo.prototype.bar = 1;
 * </code>
 * This property may only hold number values, and an assignment to any
 * other type of value is an error.
 *
 * INFERRED properties do not have an explicit type annotation. Rather,
 * we try to find all the possible types that this property can hold.
 * <code>
 * Foo.prototype.bar = 1;
 * </code>
 * If the programmer assigns other types of values to this property,
 * the property will take on the union of all these types.
 *
 * UNKNOWN properties are properties on the UNKNOWN type. The UNKNOWN
 * type has all properties, but we do not know whether they are
 * declared or inferred.
 */
public abstract class ObjectType extends JSType {

    private boolean unknown = true;

    ObjectType(JSTypeRegistry registry) {
        super(registry);
    }

    /**
     * Gets the reference name for this object. This includes named types
     * like constructors, prototypes, and enums. It notably does not include
     * literal types like strings and booleans and structural types.
     * @return the object's name or {@code null} if this is an anonymous
     *         object
     */
    public abstract String getReferenceName();

    /**
     * Gets this object's constructor.
     * @return this object's constructor or {@code null} if it is a native
     * object (constructed natively v.s. by instantiation of a function)
     */
    public abstract FunctionType getConstructor();

    /**
     * Gets the implicit prototype (a.k.a. the {@code [[Prototype]]} property).
     */
    public abstract ObjectType getImplicitPrototype();

    /**
     * Defines a property whose type is synthesized (i.e. not inferred).
     * @param propertyName the property's name
     * @param type the type
     * @param inExterns {@code true} if this property was defined in an externs
     *        file. TightenTypes assumes that any function passed to an externs
     *        property could be called, so setting this incorrectly could result
     *        in live code being removed.
     */
    public final boolean defineDeclaredProperty(String propertyName, JSType type, boolean inExterns) {
        // All property definitions go through this method
        // or defineInferredProperty.
        registry.registerPropertyOnType(propertyName, this);
        return defineProperty(propertyName, type, false, inExterns);
    }

    /**
     * Defines a property.<p>
     *
     * For clarity, callers should prefer {@link #defineDeclaredProperty} and
     * {@link #defineInferredProperty}.
     *
     * @param propertyName the property's name
     * @param type the type
     * @param inferred {@code true} if this property's type is inferred
     * @param inExterns {@code true} if this property was defined in an externs
     *        file. TightenTypes assumes that any function passed to an externs
     *        property could be called, so setting this incorrectly could result
     *        in live code being removed.
     * @return True if the property was registered successfully, false if this
     *        conflicts with a previous property type declaration.
     */
    abstract boolean defineProperty(String propertyName, JSType type, boolean inferred, boolean inExterns);

    /**
     * Gets the property type of the property whose name is given. If the
     * underlying object does not have this property, the Unknown type is
     * returned to indicate that no information is available on this property.
     *
     * @return the property's type or {@link UnknownType}. This method never
     *         returns {@code null}.
     */
    public abstract JSType getPropertyType(String propertyName);

    /**
     * Checks whether the property whose name is given is present on the
     * object.
     */
    public abstract boolean hasProperty(String propertyName);

    /**
     * Checks whether the property whose name is given is present directly on
     * the object.  Returns false even if it is declared on a supertype.
     */
    public boolean hasOwnProperty(String propertyName) {
        return hasProperty(propertyName);
    }

    /**
     * Checks whether the property's type is declared.
     */
    public abstract boolean isPropertyTypeDeclared(String propertyName);

    /**
     * Whether the given property is declared on this object.
     */
    boolean hasOwnDeclaredProperty(String name) {
        return hasOwnProperty(name) && isPropertyTypeDeclared(name);
    }

    /**
     * Gets the number of properties of this object.
     */
    public abstract int getPropertiesCount();

    @Override
    public <T> T visit(Visitor<T> visitor) {
        return visitor.caseObjectType(this);
    }

    /**
     * Checks that the prototype is an implicit prototype of this object. Since
     * each object has an implicit prototype, an implicit prototype's
     * implicit prototype is also this implicit prototype's.
     *
     * @param prototype any prototype based object
     *
     * @return {@code true} if {@code prototype} is {@code equal} to any
     *         object in this object's implicit prototype chain.
     */
    final boolean isImplicitPrototype(ObjectType prototype) {
        for (ObjectType current = this; current != null; current = current.getImplicitPrototype()) {
            if (current.equals(prototype)) {
                return true;
            }
        }
        return false;
    }

    /**
     * We treat this as the unknown type if any of its implicit prototype
     * properties is unknown.
     */
    @Override
    public boolean isUnknownType() {
        // If the object is unknown now, check the supertype again,
        // because it might have been resolved since the last check.
        if (unknown) {
            ObjectType implicitProto = getImplicitPrototype();
            if (implicitProto == null || implicitProto.isNativeObjectType()) {
                unknown = false;
            } else {
                unknown = implicitProto.isUnknownType();
            }
        }
        return unknown;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    /**
     * Returns true if any cached valeus have been set for this type.  If true,
     * then the prototype chain should not be changed, as it might invalidate the
     * cached values.
     */
    public boolean hasCachedValues() {
        return !unknown;
    }

    /**
     * Whether this is a built-in object.
     */
    public boolean isNativeObjectType() {
        return false;
    }

    /**
     * A null-safe version of JSType#toObjectType.
     */
    public static ObjectType cast(JSType type) {
        return type == null ? null : type.toObjectType();
    }

    /**
     * Gets the interfaces implemented by the ctor associated with this type.
     * Intended to be overridden by subclasses.
     */
    public Iterable<ObjectType> getCtorImplementedInterfaces() {
        return ImmutableSet.of();
    }
}
