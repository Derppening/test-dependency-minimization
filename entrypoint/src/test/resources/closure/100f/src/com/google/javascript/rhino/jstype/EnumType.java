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

import static com.google.javascript.rhino.jstype.TernaryValue.FALSE;
import static com.google.javascript.rhino.jstype.TernaryValue.TRUE;

import com.google.javascript.rhino.ErrorReporter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * An enum type representing a branded collection of elements. Each element
 * is referenced by its name, and has an {@link EnumElementType} type.
*
*
 */
public class EnumType extends PrototypeObjectType {
  private static final long serialVersionUID = 1L;

  // the type of the individual elements
  private EnumElementType elementsType;
  // the elements' names (they all have the same type)
  private final Set<String> elements = new HashSet<String>();

  /**
   * Creates an enum type.
   *
   * @param name the enum's name
   * @param elementsType the base type of the individual elements
   */
  EnumType(JSTypeRegistry registry, String name, JSType elementsType) {
    super(registry, "enum{" + name + "}", null);
    this.elementsType = new EnumElementType(registry, elementsType, name);
  }

  @Override
  public boolean isEnumType() {
    return true;
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
  }

  /**
   * Gets the elements defined on this enum.
   * @return the elements' names defined on this enum. The returned set is
   *         immutable.
   */
  public Set<String> getElements() {
    return Collections.unmodifiableSet(elements);
  }

  public boolean defineElement(String name) {
    elements.add(name);
    return defineDeclaredProperty(name, elementsType, false);
  }

  /**
   * Gets the elements' type.
   */
  public EnumElementType getElementsType() {
    return elementsType;
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    TernaryValue result = super.testForEquality(that);
    if (result != null) {
      return result;
    }
    return this.isEquivalentTo(that) ? TRUE : FALSE;
  }

  @Override
  public boolean isSubtype(JSType that) {
    return that.isEquivalentTo(getNativeType(JSTypeNative.OBJECT_TYPE)) ||
        that.isEquivalentTo(getNativeType(JSTypeNative.OBJECT_PROTOTYPE)) ||
        JSType.isSubtype(this, that);
  }

  @Override
  public String toString() {
    return getReferenceName();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseObjectType(this);
  }

  @Override
  public FunctionType getConstructor() {
    return null;
  }

  @Override
  public boolean matchesNumberContext() {
    return false;
  }

  @Override
  public boolean matchesStringContext() {
    return true;
  }

  @Override
  public boolean matchesObjectContext() {
    return true;
  }

  @Override
  JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope) {
    elementsType = (EnumElementType) elementsType.resolve(t, scope);
    return super.resolveInternal(t, scope);
  }
}
