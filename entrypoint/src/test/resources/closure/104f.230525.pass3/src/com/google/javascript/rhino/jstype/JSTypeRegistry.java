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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The type registry is used to resolve named types.
 *
 * <p>This class is not thread-safe.
 */
public class JSTypeRegistry implements Serializable {

    private static final long serialVersionUID = 1L;

    // TODO(user): An instance of this class should be used during
    // compilation. We also want to make all types' constructors package private
    // and force usage of this registry instead. This will allow us to evolve the
    // types without being tied by an open API.
    private final transient ErrorReporter reporter;

    // We use an Array instead of an immutable list because this lookup needs
    // to be very fast. When it was an immutable list, we were spending 5% of
    // CPU time on bounds checking inside get().
    private final JSType[] nativeTypes;

    private final Map<String, JSType> namesToTypes;

    // Set of namespaces in which types (or other namespaces) exist.
    private final Set<String> namespaces = new HashSet<String>();

    // NOTE(nicksantos): This is a terrible terrible hack. When type expressions
    // are evaluated, we need to be able to decide whether that type name
    // resolves to a nullable type or a non-nullable type. Object types are
    // nullable, but enum types are not.
    //
    // Notice that it's not good enough to just declare enum types sooner.
    // For example, if we have
    // /** @enum {MyObject} */ var MyEnum = ...;
    // we won't be to declare "MyEnum" without evaluating the expression
    // {MyObject}, and following those dependencies starts to lead us into
    // undecidable territory. Instead, we "pre-declare" enum types,
    // so that the expression resolver can decide whether a given name is
    // nullable or not.
    private final Set<String> enumTypeNames = new HashSet<String>();

    // Types that have been "forward-declared."
    // If these types are not declared anywhere in the binary, we shouldn't
    // try to type-check them at all.
    private final Set<String> forwardDeclaredTypes = new HashSet<String>();

    // A map of properties to the types on which those properties have been
    // declared.
    private final Map<String, Set<ObjectType>> typesIndexedByProperty = Maps.newHashMap();

    // A map of properties to the greatest subtype on which those properties have
    // been declared. This is filled lazily from the types declared in
    // typesIndexedByProperty.
    private final Map<String, JSType> greatestSubtypeByProperty = Maps.newHashMap();

    // A map from interface name to types that implement it.
    private final Multimap<String, FunctionType> interfaceToImplementors = HashMultimap.create();

    // All the unresolved named types.
    private final Multimap<StaticScope<JSType>, NamedType> unresolvedNamedTypes = ArrayListMultimap.create();

    // All the resolved named types.
    private final Multimap<StaticScope<JSType>, NamedType> resolvedNamedTypes = ArrayListMultimap.create();

    // NamedType warns about unresolved types in the last generation.
    private boolean lastGeneration = true;

    /**
     * Constructs a new type registry populated with the built-in types.
     */
    public JSTypeRegistry(ErrorReporter reporter) {
        this.reporter = reporter;
        nativeTypes = new JSType[JSTypeNative.values().length];
        namesToTypes = new HashMap<String, JSType>();
        resetForTypeCheck();
    }

    /**
     * Reset to run the TypeCheck pass.
     */
    public void resetForTypeCheck() {
        typesIndexedByProperty.clear();
        initializeBuiltInTypes();
        namesToTypes.clear();
        namespaces.clear();
        initializeRegistry();
    }

    private void initializeBuiltInTypes() {
        // These locals shouldn't be all caps.
        BooleanType BOOLEAN_TYPE = new BooleanType(this);
        registerNativeType(JSTypeNative.BOOLEAN_TYPE, BOOLEAN_TYPE);
        NullType NULL_TYPE = new NullType(this);
        registerNativeType(JSTypeNative.NULL_TYPE, NULL_TYPE);
        NumberType NUMBER_TYPE = new NumberType(this);
        registerNativeType(JSTypeNative.NUMBER_TYPE, NUMBER_TYPE);
        StringType STRING_TYPE = new StringType(this);
        registerNativeType(JSTypeNative.STRING_TYPE, STRING_TYPE);
        UnknownType UNKNOWN_TYPE = new UnknownType(this, false);
        registerNativeType(JSTypeNative.UNKNOWN_TYPE, UNKNOWN_TYPE);
        registerNativeType(JSTypeNative.CHECKED_UNKNOWN_TYPE, new UnknownType(this, true));
        VoidType VOID_TYPE = new VoidType(this);
        registerNativeType(JSTypeNative.VOID_TYPE, VOID_TYPE);
        AllType ALL_TYPE = new AllType(this);
        registerNativeType(JSTypeNative.ALL_TYPE, ALL_TYPE);
        // Top Level Prototype (the One)
        // The initializations of TOP_LEVEL_PROTOTYPE and OBJECT_FUNCTION_TYPE
        // use each other's results, so at least one of them will get null
        // instead of an actual type; however, this seems to be benign.
        ObjectType TOP_LEVEL_PROTOTYPE = new FunctionPrototypeType(this, null, null, true);
        registerNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE, TOP_LEVEL_PROTOTYPE);
        // Object
        FunctionType OBJECT_FUNCTION_TYPE = new FunctionType(this, "Object", null, createOptionalParameters(ALL_TYPE), UNKNOWN_TYPE, null, null, true, true);
        OBJECT_FUNCTION_TYPE.defineDeclaredProperty("prototype", TOP_LEVEL_PROTOTYPE, true);
        registerNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE, OBJECT_FUNCTION_TYPE);
        ObjectType OBJECT_PROTOTYPE = OBJECT_FUNCTION_TYPE.getPrototype();
        registerNativeType(JSTypeNative.OBJECT_PROTOTYPE, OBJECT_PROTOTYPE);
        ObjectType OBJECT_TYPE = OBJECT_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.OBJECT_TYPE, OBJECT_TYPE);
        // Function
        FunctionType FUNCTION_FUNCTION_TYPE = new FunctionType(this, "Function", null, createParametersWithVarArgs(ALL_TYPE), UNKNOWN_TYPE, null, null, true, true);
        FUNCTION_FUNCTION_TYPE.setPrototypeBasedOn(OBJECT_TYPE);
        registerNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE, FUNCTION_FUNCTION_TYPE);
        ObjectType FUNCTION_PROTOTYPE = FUNCTION_FUNCTION_TYPE.getPrototype();
        registerNativeType(JSTypeNative.FUNCTION_PROTOTYPE, FUNCTION_PROTOTYPE);
        NoType NO_TYPE = new NoType(this);
        registerNativeType(JSTypeNative.NO_TYPE, NO_TYPE);
        NoObjectType NO_OBJECT_TYPE = new NoObjectType(this);
        registerNativeType(JSTypeNative.NO_OBJECT_TYPE, NO_OBJECT_TYPE);
        // Array
        FunctionType ARRAY_FUNCTION_TYPE = new FunctionType(this, "Array", null, createParametersWithVarArgs(ALL_TYPE), null, null, null, true, true) {

            private static final long serialVersionUID = 1L;

            @Override
            public JSType getReturnType() {
                return getInstanceType();
            }
        };
        ObjectType arrayPrototype = ARRAY_FUNCTION_TYPE.getPrototype();
        registerNativeType(JSTypeNative.ARRAY_FUNCTION_TYPE, ARRAY_FUNCTION_TYPE);
        ObjectType ARRAY_TYPE = ARRAY_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.ARRAY_TYPE, ARRAY_TYPE);
        // Boolean
        FunctionType BOOLEAN_OBJECT_FUNCTION_TYPE = new FunctionType(this, "Boolean", null, createParameters(false, ALL_TYPE), BOOLEAN_TYPE, null, null, true, true);
        ObjectType booleanPrototype = BOOLEAN_OBJECT_FUNCTION_TYPE.getPrototype();
        registerNativeType(JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE, BOOLEAN_OBJECT_FUNCTION_TYPE);
        ObjectType BOOLEAN_OBJECT_TYPE = BOOLEAN_OBJECT_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE, BOOLEAN_OBJECT_TYPE);
        // Date
        FunctionType DATE_FUNCTION_TYPE = new FunctionType(this, "Date", null, createOptionalParameters(UNKNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE), STRING_TYPE, null, null, true, true);
        ObjectType datePrototype = DATE_FUNCTION_TYPE.getPrototype();
        registerNativeType(JSTypeNative.DATE_FUNCTION_TYPE, DATE_FUNCTION_TYPE);
        ObjectType DATE_TYPE = DATE_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.DATE_TYPE, DATE_TYPE);
        // Error
        FunctionType ERROR_FUNCTION_TYPE = new ErrorFunctionType(this, "Error");
        registerNativeType(JSTypeNative.ERROR_FUNCTION_TYPE, ERROR_FUNCTION_TYPE);
        ObjectType ERROR_TYPE = ERROR_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.ERROR_TYPE, ERROR_TYPE);
        // EvalError
        FunctionType EVAL_ERROR_FUNCTION_TYPE = new ErrorFunctionType(this, "EvalError");
        EVAL_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
        registerNativeType(JSTypeNative.EVAL_ERROR_FUNCTION_TYPE, EVAL_ERROR_FUNCTION_TYPE);
        ObjectType EVAL_ERROR_TYPE = EVAL_ERROR_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.EVAL_ERROR_TYPE, EVAL_ERROR_TYPE);
        // RangeError
        FunctionType RANGE_ERROR_FUNCTION_TYPE = new ErrorFunctionType(this, "RangeError");
        RANGE_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
        registerNativeType(JSTypeNative.RANGE_ERROR_FUNCTION_TYPE, RANGE_ERROR_FUNCTION_TYPE);
        ObjectType RANGE_ERROR_TYPE = RANGE_ERROR_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.RANGE_ERROR_TYPE, RANGE_ERROR_TYPE);
        // ReferenceError
        FunctionType REFERENCE_ERROR_FUNCTION_TYPE = new ErrorFunctionType(this, "ReferenceError");
        REFERENCE_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
        registerNativeType(JSTypeNative.REFERENCE_ERROR_FUNCTION_TYPE, REFERENCE_ERROR_FUNCTION_TYPE);
        ObjectType REFERENCE_ERROR_TYPE = REFERENCE_ERROR_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.REFERENCE_ERROR_TYPE, REFERENCE_ERROR_TYPE);
        // SyntaxError
        FunctionType SYNTAX_ERROR_FUNCTION_TYPE = new ErrorFunctionType(this, "SyntaxError");
        SYNTAX_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
        registerNativeType(JSTypeNative.SYNTAX_ERROR_FUNCTION_TYPE, SYNTAX_ERROR_FUNCTION_TYPE);
        ObjectType SYNTAX_ERROR_TYPE = SYNTAX_ERROR_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.SYNTAX_ERROR_TYPE, SYNTAX_ERROR_TYPE);
        // TypeError
        FunctionType TYPE_ERROR_FUNCTION_TYPE = new ErrorFunctionType(this, "TypeError");
        TYPE_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
        registerNativeType(JSTypeNative.TYPE_ERROR_FUNCTION_TYPE, TYPE_ERROR_FUNCTION_TYPE);
        ObjectType TYPE_ERROR_TYPE = TYPE_ERROR_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.TYPE_ERROR_TYPE, TYPE_ERROR_TYPE);
        // URIError
        FunctionType URI_ERROR_FUNCTION_TYPE = new ErrorFunctionType(this, "URIError");
        URI_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
        registerNativeType(JSTypeNative.URI_ERROR_FUNCTION_TYPE, URI_ERROR_FUNCTION_TYPE);
        ObjectType URI_ERROR_TYPE = URI_ERROR_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.URI_ERROR_TYPE, URI_ERROR_TYPE);
        // Number
        FunctionType NUMBER_OBJECT_FUNCTION_TYPE = new FunctionType(this, "Number", null, createParameters(false, ALL_TYPE), NUMBER_TYPE, null, null, true, true);
        ObjectType numberPrototype = NUMBER_OBJECT_FUNCTION_TYPE.getPrototype();
        registerNativeType(JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE, NUMBER_OBJECT_FUNCTION_TYPE);
        ObjectType NUMBER_OBJECT_TYPE = NUMBER_OBJECT_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.NUMBER_OBJECT_TYPE, NUMBER_OBJECT_TYPE);
        // RegExp
        FunctionType REGEXP_FUNCTION_TYPE = new FunctionType(this, "RegExp", null, createOptionalParameters(ALL_TYPE, ALL_TYPE), null, null, null, true, true) {

            private static final long serialVersionUID = 1L;

            @Override
            public JSType getReturnType() {
                return getInstanceType();
            }
        };
        ObjectType regexpPrototype = REGEXP_FUNCTION_TYPE.getPrototype();
        registerNativeType(JSTypeNative.REGEXP_FUNCTION_TYPE, REGEXP_FUNCTION_TYPE);
        ObjectType REGEXP_TYPE = REGEXP_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.REGEXP_TYPE, REGEXP_TYPE);
        // String
        FunctionType STRING_OBJECT_FUNCTION_TYPE = new FunctionType(this, "String", null, createParameters(false, ALL_TYPE), STRING_TYPE, null, null, true, true);
        ObjectType stringPrototype = STRING_OBJECT_FUNCTION_TYPE.getPrototype();
        registerNativeType(JSTypeNative.STRING_OBJECT_FUNCTION_TYPE, STRING_OBJECT_FUNCTION_TYPE);
        ObjectType STRING_OBJECT_TYPE = STRING_OBJECT_FUNCTION_TYPE.getInstanceType();
        registerNativeType(JSTypeNative.STRING_OBJECT_TYPE, STRING_OBJECT_TYPE);
        // (Object,string,number)
        JSType OBJECT_NUMBER_STRING = createUnionType(OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE);
        registerNativeType(JSTypeNative.OBJECT_NUMBER_STRING, OBJECT_NUMBER_STRING);
        // (Object,string,number,boolean)
        JSType OBJECT_NUMBER_STRING_BOOLEAN = createUnionType(OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE, BOOLEAN_TYPE);
        registerNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN, OBJECT_NUMBER_STRING_BOOLEAN);
        // (string,number,boolean)
        JSType NUMBER_STRING_BOOLEAN = createUnionType(NUMBER_TYPE, STRING_TYPE, BOOLEAN_TYPE);
        registerNativeType(JSTypeNative.NUMBER_STRING_BOOLEAN, NUMBER_STRING_BOOLEAN);
        // (string,number)
        JSType NUMBER_STRING = createUnionType(NUMBER_TYPE, STRING_TYPE);
        registerNativeType(JSTypeNative.NUMBER_STRING, NUMBER_STRING);
        // Native object properties are filled in by externs...
        // (String, string)
        JSType STRING_VALUE_OR_OBJECT_TYPE = createUnionType(STRING_OBJECT_TYPE, STRING_TYPE);
        registerNativeType(JSTypeNative.STRING_VALUE_OR_OBJECT_TYPE, STRING_VALUE_OR_OBJECT_TYPE);
        // (Number, number)
        JSType NUMBER_VALUE_OR_OBJECT_TYPE = createUnionType(NUMBER_OBJECT_TYPE, NUMBER_TYPE);
        registerNativeType(JSTypeNative.NUMBER_VALUE_OR_OBJECT_TYPE, NUMBER_VALUE_OR_OBJECT_TYPE);
        // unknown function type, i.e. (?...) -> ?
        FunctionType U2U_FUNCTION_TYPE = createFunctionType(UNKNOWN_TYPE, true, UNKNOWN_TYPE);
        registerNativeType(JSTypeNative.U2U_FUNCTION_TYPE, U2U_FUNCTION_TYPE);
        // unknown constructor type, i.e. (?...) -> ? with the NoObject type
        // as instance type
        // This is equivalent to
        FunctionType // This is equivalent to
        // createConstructorType(UNKNOWN_TYPE, true, UNKNOWN_TYPE), but,
        U2U_CONSTRUCTOR_TYPE = // in addition, overrides getInstanceType() to return the NoObject type
        // instead of a new anonymous object.
        new FunctionType(this, "Function", null, createParametersWithVarArgs(UNKNOWN_TYPE), UNKNOWN_TYPE, NO_OBJECT_TYPE, null, true, true) {

            private static final long serialVersionUID = 1L;

            @Override
            public FunctionType getConstructor() {
                return registry.getNativeFunctionType(JSTypeNative.FUNCTION_FUNCTION_TYPE);
            }
        };
        // The U2U_CONSTRUCTOR is weird, because it's the supertype of its
        // own constructor.
        registerNativeType(JSTypeNative.U2U_CONSTRUCTOR_TYPE, U2U_CONSTRUCTOR_TYPE);
        registerNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE, U2U_CONSTRUCTOR_TYPE);
        FUNCTION_FUNCTION_TYPE.setInstanceType(U2U_CONSTRUCTOR_TYPE);
        U2U_CONSTRUCTOR_TYPE.setImplicitPrototype(FUNCTION_PROTOTYPE);
        // least function type, i.e. (All...) -> NoType
        FunctionType LEAST_FUNCTION_TYPE = createFunctionType(NO_TYPE, true, ALL_TYPE);
        registerNativeType(JSTypeNative.LEAST_FUNCTION_TYPE, LEAST_FUNCTION_TYPE);
        // the 'this' object in the global scope
        ObjectType GLOBAL_THIS = createObjectType("global this", null, UNKNOWN_TYPE);
        registerNativeType(JSTypeNative.GLOBAL_THIS, GLOBAL_THIS);
        // greatest function type, i.e. (NoType...) -> All
        FunctionType GREATEST_FUNCTION_TYPE = createFunctionType(ALL_TYPE, true, NO_TYPE);
        registerNativeType(JSTypeNative.GREATEST_FUNCTION_TYPE, GREATEST_FUNCTION_TYPE);
    }

    private void initializeRegistry() {
        register(getNativeType(JSTypeNative.ARRAY_TYPE));
        register(getNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE));
        register(getNativeType(JSTypeNative.BOOLEAN_TYPE));
        register(getNativeType(JSTypeNative.DATE_TYPE));
        register(getNativeType(JSTypeNative.NULL_TYPE));
        register(getNativeType(JSTypeNative.NULL_TYPE), "Null");
        register(getNativeType(JSTypeNative.NUMBER_OBJECT_TYPE));
        register(getNativeType(JSTypeNative.NUMBER_TYPE));
        register(getNativeType(JSTypeNative.OBJECT_TYPE));
        register(getNativeType(JSTypeNative.ERROR_TYPE));
        register(getNativeType(JSTypeNative.URI_ERROR_TYPE));
        register(getNativeType(JSTypeNative.EVAL_ERROR_TYPE));
        register(getNativeType(JSTypeNative.TYPE_ERROR_TYPE));
        register(getNativeType(JSTypeNative.RANGE_ERROR_TYPE));
        register(getNativeType(JSTypeNative.REFERENCE_ERROR_TYPE));
        register(getNativeType(JSTypeNative.SYNTAX_ERROR_TYPE));
        register(getNativeType(JSTypeNative.REGEXP_TYPE));
        register(getNativeType(JSTypeNative.STRING_OBJECT_TYPE));
        register(getNativeType(JSTypeNative.STRING_TYPE));
        register(getNativeType(JSTypeNative.VOID_TYPE));
        register(getNativeType(JSTypeNative.VOID_TYPE), "Undefined");
        register(getNativeType(JSTypeNative.VOID_TYPE), "void");
        register(getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE), "Function");
    }

    private void register(JSType type) {
        register(type, type.toString());
    }

    private void register(JSType type, String name) {
        namesToTypes.put(name, type);
        // Add all the namespaces in which this name lives.
        while (name.indexOf('.') > 0) {
            name = name.substring(0, name.lastIndexOf('.'));
            namespaces.add(name);
        }
    }

    private void registerNativeType(JSTypeNative typeId, JSType type) {
        nativeTypes[typeId.ordinal()] = type;
    }

    /**
     * Tells the type system that {@code owner} may have a property named
     * {@code propertyName}. This allows the registry to keep track of what
     * types a property is defined upon.
     *
     * This is NOT the same as saying that {@code owner} must have a property
     * named type. ObjectType#hasProperty attempts to minimize false positives
     * ("if we're not sure, then don't type check this property"). The type
     * registry, on the other hand, should attempt to minimize false negatives
     * ("if this property is assigned anywhere in the program, it must
     * show up in the type registry").
     */
    public void registerPropertyOnType(String propertyName, ObjectType owner) {
        Set<ObjectType> typeSet = typesIndexedByProperty.get(propertyName);
        if (typeSet == null) {
            typesIndexedByProperty.put(propertyName, typeSet = Sets.newHashSet());
        }
        greatestSubtypeByProperty.remove(propertyName);
        typeSet.add(owner);
    }

    public JSType getNativeType(JSTypeNative typeId) {
        return nativeTypes[typeId.ordinal()];
    }

    public ObjectType getNativeObjectType(JSTypeNative typeId) {
        return (ObjectType) getNativeType(typeId);
    }

    public FunctionType getNativeFunctionType(JSTypeNative typeId) {
        return (FunctionType) getNativeType(typeId);
    }

    /**
     * Creates a type representing optional values of the given type.
     * @return the union of the type and the void type
     */
    public JSType createOptionalType(JSType type) {
        if (type instanceof UnknownType || type.isAllType()) {
            return type;
        } else {
            return createUnionType(type, getNativeType(JSTypeNative.VOID_TYPE));
        }
    }

    /**
     * Creates a type representing nullable values of the given type.
     * @return the union of the type and the Null type
     */
    public JSType createNullableType(JSType type) {
        return createUnionType(type, getNativeType(JSTypeNative.NULL_TYPE));
    }

    /**
     * Creates a nullabel and undefine-able value of the given type.
     * @return The union of the type and null and undefined.
     */
    public JSType createOptionalNullableType(JSType type) {
        return createUnionType(type, getNativeType(JSTypeNative.VOID_TYPE), getNativeType(JSTypeNative.NULL_TYPE));
    }

    /**
     * Creates a union type whose variants are the arguments.
     */
    public JSType createUnionType(JSType... variants) {
        UnionTypeBuilder builder = new UnionTypeBuilder(this);
        for (JSType type : variants) {
            builder.addAlternate(type);
        }
        return builder.build();
    }

    /**
     * Creates a union type whose variants are the builtin types specified
     * by the arguments.
     */
    public JSType createUnionType(JSTypeNative... variants) {
        UnionTypeBuilder builder = new UnionTypeBuilder(this);
        for (JSTypeNative typeId : variants) {
            builder.addAlternate(getNativeType(typeId));
        }
        return builder.build();
    }

    /**
     * Creates a function type.
     *
     * @param returnType the function's return type
     * @param parameterTypes the parameters' types
     */
    public FunctionType createFunctionType(JSType returnType, JSType... parameterTypes) {
        return new FunctionType(this, null, null, createParameters(parameterTypes), returnType);
    }

    /**
     * Creates a function type. The last parameter type of the function is
     * considered a variable length argument.
     *
     * @param returnType the function's return type
     * @param parameterTypes the parameters' types
     */
    public FunctionType createFunctionTypeWithVarArgs(JSType returnType, JSType... parameterTypes) {
        return new FunctionType(this, null, null, createParametersWithVarArgs(parameterTypes), returnType);
    }

    /**
     * Creates a tree hierarchy representing a typed argument list.
     *
     * @param parameterTypes the parameter types.
     * @return a tree hierarchy representing a typed argument list.
     */
    public Node createParameters(JSType... parameterTypes) {
        return createParameters(false, parameterTypes);
    }

    /**
     * Creates a tree hierarchy representing a typed argument list. The last
     * parameter type is considered a variable length argument.
     *
     * @param parameterTypes the parameter types. The last element of this array
     *     is considered a variable length argument.
     * @return a tree hierarchy representing a typed argument list.
     */
    public Node createParametersWithVarArgs(JSType... parameterTypes) {
        return createParameters(true, parameterTypes);
    }

    /**
     * Creates a tree hierarchy representing a typed parameter list in which
     * every parameter is optional.
     */
    public Node createOptionalParameters(JSType... parameterTypes) {
        FunctionParamBuilder builder = new FunctionParamBuilder(this);
        builder.addOptionalParams(parameterTypes);
        return builder.build();
    }

    /**
     * Creates a tree hierarchy representing a typed argument list.
     *
     * @param lastVarArgs whether the last type should considered as a variable
     *     length argument.
     * @param parameterTypes the parameter types. The last element of this array
     *     is considered a variable length argument is {@code lastVarArgs} is
     *     {@code true}.
     * @return a tree hierarchy representing a typed argument list
     */
    private Node createParameters(boolean lastVarArgs, JSType... parameterTypes) {
        FunctionParamBuilder builder = new FunctionParamBuilder(this);
        int max = parameterTypes.length - 1;
        for (int i = 0; i <= max; i++) {
            if (lastVarArgs && i == max) {
                builder.addVarArgs(parameterTypes[i]);
            } else {
                builder.addRequiredParams(parameterTypes[i]);
            }
        }
        return builder.build();
    }

    /**
     * Creates a function type.
     * @param returnType the function's return type
     * @param lastVarArgs whether the last parameter type should be considered as
     * an extensible var_args parameter
     * @param parameterTypes the parameters' types
     */
    public FunctionType createFunctionType(JSType returnType, boolean lastVarArgs, JSType... parameterTypes) {
        if (lastVarArgs) {
            return createFunctionTypeWithVarArgs(returnType, parameterTypes);
        } else {
            return createFunctionType(returnType, parameterTypes);
        }
    }

    /**
     * Create an object type.
     */
    public ObjectType createObjectType(String name, Node n, ObjectType implicitPrototype) {
        return new PrototypeObjectType(this, name, implicitPrototype);
    }
}
