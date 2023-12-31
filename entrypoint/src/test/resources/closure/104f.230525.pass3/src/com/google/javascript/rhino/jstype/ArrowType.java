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

import com.google.javascript.rhino.Node;

/**
 * The arrow type is an internal type that models the functional arrow type
 * seen in typical functional programming languages.  It is used soley for
 * separating the management of the arrow type from the complex
 * {@link FunctionType} that models JavaScript's notion of functions.
 */
final class ArrowType extends JSType {

    private static final long serialVersionUID = 1L;

    final Node parameters;

    JSType returnType;

    ArrowType(JSTypeRegistry registry, Node parameters, JSType returnType) {
        super(registry);
        this.parameters = parameters;
        this.returnType = returnType;
    }

    @Override
    public boolean isSubtype(JSType other) {
        if (!(other instanceof ArrowType)) {
            return false;
        }
        ArrowType that = (ArrowType) other;
        // this.returnType <: that.returnType (covariant)
        // If the return type is null, this is equivalent to unknown so we do not
        // base our decision on that.
        if (this.returnType != null && that.returnType != null && !this.returnType.isSubtype(that.returnType)) {
            return false;
        }
        // that.paramType[i] <: this.paramType[i] (contravariant)
        // TODO(nicksantos): This is incorrect. It should be invariant.
        // Follow up with closure team on how to fix this without everyone
        // hating on us.
        //
        // If the parameter list is null, this is equivalent of ?... so we do not
        // base our decision on that.
        if (this.parameters != null && that.parameters != null) {
            Node thisParam = parameters.getFirstChild();
            Node thatParam = that.parameters.getFirstChild();
            while (thisParam != null && thatParam != null) {
                JSType thisParamType = thisParam.getJSType();
                if (thisParamType != null) {
                    JSType thatParamType = thatParam.getJSType();
                    if (thatParamType == null || !thatParamType.isSubtype(thisParamType)) {
                        return false;
                    }
                }
                boolean thisIsVarArgs = thisParam.isVarArgs();
                boolean thatIsVarArgs = thatParam.isVarArgs();
                // don't advance if we have variable arguments
                if (!thisIsVarArgs) {
                    thisParam = thisParam.getNext();
                }
                if (!thatIsVarArgs) {
                    thatParam = thatParam.getNext();
                }
                // both var_args indicates the end
                if (thisIsVarArgs && thatIsVarArgs) {
                    thisParam = null;
                    thatParam = null;
                }
            }
            // Right now, the parser's type system doesn't have a good way
            // to model optional arguments.
            //
            // Suppose we have
            // function f(number, number) {}
            // function g(number) {}
            // If the second arg of f is optional, then f is a subtype of g,
            // but g is not a subtype of f.
            // If the second arg of f is required, then g is a subtype of f,
            // but f is not a subtype of g.
            //
            // Until we model optional params, let's just punt on this.
            // If one type has more arguments than the other, we won't check them.
            //
            // NOTE(nicksantos): This is described in Draft 2 of the ES4 spec,
            // Section 3.4.6: Subtyping Function Types. It seems really
            // strange but I haven't thought a lot about the implementation.
        }
        return true;
    }

    @Override
    public boolean equals(Object object) {
        // Please keep this method in sync with the hashCode() method below.
        if (!(object instanceof ArrowType)) {
            return false;
        }
        ArrowType that = (ArrowType) object;
        // if both return types are specified, then they should be equal
        if (returnType == null) {
            if (that.returnType != null) {
                return false;
            }
        } else {
            if (that.returnType == null) {
                return false;
            }
            if (!returnType.equals(that.returnType)) {
                return false;
            }
        }
        // if both types include parameters, the lists should be the same
        if (parameters == null) {
            return that.parameters == null;
        } else if (that.parameters == null) {
            return false;
        }
        Node thisParam = parameters.getFirstChild();
        Node otherParam = that.parameters.getFirstChild();
        while (thisParam != null && otherParam != null) {
            JSType thisParamType = thisParam.getJSType();
            JSType otherParamType = otherParam.getJSType();
            if (thisParamType != null) {
                // Both parameter lists give a type for this param, it should be equal
                if (otherParamType != null && !thisParamType.equals(otherParamType)) {
                    return false;
                }
            } else {
                if (otherParamType != null) {
                    return false;
                }
            }
            thisParam = thisParam.getNext();
            otherParam = otherParam.getNext();
        }
        // One of the parameters is null, so the types are only equal if both
        // parameter lists are null (they are equal).
        return thisParam == otherParam;
    }

    @Override
    public int hashCode() {
        int hashCode = 0;
        if (returnType != null) {
            hashCode += returnType.hashCode();
        }
        if (parameters != null) {
            Node param = parameters.getFirstChild();
            while (param != null) {
                JSType paramType = param.getJSType();
                if (paramType != null) {
                    hashCode += paramType.hashCode();
                }
                param = param.getNext();
            }
        }
        return hashCode;
    }

    @Override
    public JSType getGreatestSubtype(JSType that) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T visit(Visitor<T> visitor) {
        throw new UnsupportedOperationException();
    }
}
