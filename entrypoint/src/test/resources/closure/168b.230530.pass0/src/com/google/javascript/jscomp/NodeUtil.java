/*
 * Copyright 2004 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * NodeUtil contains utilities that get properties from the Node object.
 */
public final class NodeUtil {

    static final long MAX_POSITIVE_INTEGER_NUMBER = (long) Math.pow(2, 53);

    final static String JSC_PROPERTY_NAME_FN = "JSCompiler_renameProperty";

    // TODO(user): Eliminate this class and make all of the static methods
    // instance methods of com.google.javascript.rhino.Node.
    /**
     * the set of builtin constructors that don't have side effects.
     */
    private static final Set<String> CONSTRUCTORS_WITHOUT_SIDE_EFFECTS = new HashSet<String>(Arrays.asList("Array", "Date", "Error", "Object", "RegExp", "XMLHttpRequest"));

    // Utility class; do not instantiate.
    private NodeUtil() {
        throw new AssertionError("This method should not be reached! Signature: NodeUtil()");
    }

    /**
     * Gets the boolean value of a node that represents a literal. This method
     * effectively emulates the <code>Boolean()</code> JavaScript cast function
     * except it return UNKNOWN for known values with side-effects, use
     * getExpressionBooleanValue if you don't care about side-effects.
     */
    static TernaryValue getPureBooleanValue(Node n) {
        switch(n.getType()) {
            case Token.STRING:
                return TernaryValue.forBoolean(n.getString().length() > 0);
            case Token.NUMBER:
                return TernaryValue.forBoolean(n.getDouble() != 0);
            case Token.NOT:
                return getPureBooleanValue(n.getLastChild()).not();
            case Token.NULL:
            case Token.FALSE:
                return TernaryValue.FALSE;
            case Token.VOID:
                if (!mayHaveSideEffects(n.getFirstChild())) {
                    return TernaryValue.FALSE;
                }
                break;
            case Token.NAME:
                String name = n.getString();
                if ("undefined".equals(name) || "NaN".equals(name)) {
                    // We assume here that programs don't change the value of the keyword
                    // undefined to something other than the value undefined.
                    return TernaryValue.FALSE;
                } else if ("Infinity".equals(name)) {
                    return TernaryValue.TRUE;
                }
                break;
            case Token.TRUE:
            case Token.REGEXP:
                return TernaryValue.TRUE;
            case Token.ARRAYLIT:
            case Token.OBJECTLIT:
                if (!mayHaveSideEffects(n)) {
                    return TernaryValue.TRUE;
                }
                break;
        }
        return TernaryValue.UNKNOWN;
    }

    /**
     * Gets the value of a node as a String, or null if it cannot be converted.
     * When it returns a non-null String, this method effectively emulates the
     * <code>String()</code> JavaScript cast function.
     */
    static String getStringValue(Node n) {
        // TODO(user): regex literals as well.
        switch(n.getType()) {
            case Token.STRING:
            case Token.STRING_KEY:
                return n.getString();
            case Token.NAME:
                String name = n.getString();
                if ("undefined".equals(name) || "Infinity".equals(name) || "NaN".equals(name)) {
                    return name;
                }
                break;
            case Token.NUMBER:
                return getStringValue(n.getDouble());
            case Token.FALSE:
                return "false";
            case Token.TRUE:
                return "true";
            case Token.NULL:
                return "null";
            case Token.VOID:
                return "undefined";
            case Token.NOT:
                TernaryValue child = getPureBooleanValue(n.getFirstChild());
                if (child != TernaryValue.UNKNOWN) {
                    // reversed.
                    return child.toBoolean(true) ? "false" : "true";
                }
                break;
            case Token.ARRAYLIT:
                return arrayToString(n);
            case Token.OBJECTLIT:
                return "[object Object]";
        }
        return null;
    }

    static String getStringValue(double value) {
        long longValue = (long) value;
        // Return "1" instead of "1.0"
        if (longValue == value) {
            return Long.toString(longValue);
        } else {
            return Double.toString(value);
        }
    }

    /**
     * When converting arrays to string using Array.prototype.toString or
     * Array.prototype.join, the rules for conversion to String are different
     * than converting each element individually.  Specifically, "null" and
     * "undefined" are converted to an empty string.
     * @param n A node that is a member of an Array.
     * @return The string representation.
     */
    static String getArrayElementStringValue(Node n) {
        return (NodeUtil.isNullOrUndefined(n) || n.isEmpty()) ? "" : getStringValue(n);
    }

    static String arrayToString(Node literal) {
        Node first = literal.getFirstChild();
        StringBuilder result = new StringBuilder();
        int nextSlot = 0;
        int nextSkipSlot = 0;
        for (Node n = first; n != null; n = n.getNext()) {
            String childValue = getArrayElementStringValue(n);
            if (childValue == null) {
                return null;
            }
            if (n != first) {
                result.append(',');
            }
            result.append(childValue);
            nextSlot++;
        }
        return result.toString();
    }

    /**
     * Returns true if this is an immutable value.
     */
    static boolean isImmutableValue(Node n) {
        switch(n.getType()) {
            case Token.STRING:
            case Token.NUMBER:
            case Token.NULL:
            case Token.TRUE:
            case Token.FALSE:
                return true;
            case Token.NOT:
                return isImmutableValue(n.getFirstChild());
            case Token.VOID:
            case Token.NEG:
                return isImmutableValue(n.getFirstChild());
            case Token.NAME:
                String name = n.getString();
                // We assume here that programs don't change the value of the keyword
                // undefined to something other than the value undefined.
                return "undefined".equals(name) || "Infinity".equals(name) || "NaN".equals(name);
        }
        return false;
    }

    /**
     * Returns true if this is a literal value. We define a literal value
     * as any node that evaluates to the same thing regardless of when or
     * where it is evaluated. So /xyz/ and [3, 5] are literals, but
     * the name a is not.
     *
     * Function literals do not meet this definition, because they
     * lexically capture variables. For example, if you have
     * <code>
     * function() { return a; }
     * </code>
     * If it is evaluated in a different scope, then it
     * captures a different variable. Even if the function did not read
     * any captured variables directly, it would still fail this definition,
     * because it affects the lifecycle of variables in the enclosing scope.
     *
     * However, a function literal with respect to a particular scope is
     * a literal.
     *
     * @param includeFunctions If true, all function expressions will be
     *     treated as literals.
     */
    static boolean isLiteralValue(Node n, boolean includeFunctions) {
        switch(n.getType()) {
            case Token.ARRAYLIT:
                for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
                    if ((!child.isEmpty()) && !isLiteralValue(child, includeFunctions)) {
                        return false;
                    }
                }
                return true;
            case Token.REGEXP:
                // Return true only if all children are const.
                for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
                    if (!isLiteralValue(child, includeFunctions)) {
                        return false;
                    }
                }
                return true;
            case Token.OBJECTLIT:
                // Return true only if all values are const.
                for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
                    if (!isLiteralValue(child.getFirstChild(), includeFunctions)) {
                        return false;
                    }
                }
                return true;
            case Token.FUNCTION:
                return includeFunctions && !NodeUtil.isFunctionDeclaration(n);
            default:
                return isImmutableValue(n);
        }
    }

    /**
     * Returns whether this a BLOCK node with no children.
     *
     * @param block The node.
     */
    static boolean isEmptyBlock(Node block) {
        if (!block.isBlock()) {
            return false;
        }
        for (Node n = block.getFirstChild(); n != null; n = n.getNext()) {
            if (!n.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static boolean isSimpleOperator(Node n) {
        return isSimpleOperatorType(n.getType());
    }

    /**
     * A "simple" operator is one whose children are expressions,
     * has no direct side-effects (unlike '+='), and has no
     * conditional aspects (unlike '||').
     */
    static boolean isSimpleOperatorType(int type) {
        switch(type) {
            case Token.ADD:
            case Token.BITAND:
            case Token.BITNOT:
            case Token.BITOR:
            case Token.BITXOR:
            case Token.COMMA:
            case Token.DIV:
            case Token.EQ:
            case Token.GE:
            case Token.GETELEM:
            case Token.GETPROP:
            case Token.GT:
            case Token.INSTANCEOF:
            case Token.LE:
            case Token.LSH:
            case Token.LT:
            case Token.MOD:
            case Token.MUL:
            case Token.NE:
            case Token.NOT:
            case Token.RSH:
            case Token.SHEQ:
            case Token.SHNE:
            case Token.SUB:
            case Token.TYPEOF:
            case Token.VOID:
            case Token.POS:
            case Token.NEG:
            case Token.URSH:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true if the node which may have side effects when executed.
     */
    static boolean mayHaveSideEffects(Node n) {
        return mayHaveSideEffects(n, null);
    }

    static boolean mayHaveSideEffects(Node n, AbstractCompiler compiler) {
        return checkForStateChangeHelper(n, false, compiler);
    }

    /**
     * Returns true if some node in n's subtree changes application state.
     * If {@code checkForNewObjects} is true, we assume that newly created
     * mutable objects (like object literals) change state. Otherwise, we assume
     * that they have no side effects.
     */
    private static boolean checkForStateChangeHelper(Node n, boolean checkForNewObjects, AbstractCompiler compiler) {
        // Rather than id which ops may have side effects, id the ones
        // that we know to be safe
        switch(n.getType()) {
            // other side-effect free statements and expressions
            case Token.AND:
            case Token.BLOCK:
            case Token.EXPR_RESULT:
            case Token.HOOK:
            case Token.IF:
            case Token.IN:
            case Token.PARAM_LIST:
            case Token.NUMBER:
            case Token.OR:
            case Token.THIS:
            case Token.TRUE:
            case Token.FALSE:
            case Token.NULL:
            case Token.STRING:
            case Token.STRING_KEY:
            case Token.SWITCH:
            case Token.TRY:
            case Token.EMPTY:
                break;
            // Throws are by definition side effects
            case Token.THROW:
                return true;
            case Token.OBJECTLIT:
                if (checkForNewObjects) {
                    return true;
                }
                for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
                    if (checkForStateChangeHelper(c.getFirstChild(), checkForNewObjects, compiler)) {
                        return true;
                    }
                }
                return false;
            case Token.ARRAYLIT:
            case Token.REGEXP:
                if (checkForNewObjects) {
                    return true;
                }
                break;
            // empty var statement (no declaration)
            case Token.VAR:
            case // variable by itself
            Token.NAME:
                if (n.getFirstChild() != null) {
                    return true;
                }
                break;
            case Token.FUNCTION:
                // Function expressions don't have side-effects, but function
                // declarations change the namespace. Either way, we don't need to
                // check the children, since they aren't executed at declaration time.
                return checkForNewObjects || !isFunctionExpression(n);
            case Token.NEW:
                if (checkForNewObjects) {
                    return true;
                }
                if (!constructorCallHasSideEffects(n)) {
                    // loop below will see if the constructor parameters have
                    // side-effects
                    break;
                }
                return true;
            case Token.CALL:
                // calls to functions that have no side effects have the no
                // side effect property set.
                if (!functionCallHasSideEffects(n, compiler)) {
                    // loop below will see if the function parameters have
                    // side-effects
                    break;
                }
                return true;
            default:
                if (isSimpleOperatorType(n.getType())) {
                    break;
                }
                if (isAssignmentOp(n)) {
                    Node assignTarget = n.getFirstChild();
                    if (assignTarget.isName()) {
                        return true;
                    }
                    // Assignments will have side effects if
                    // a) The RHS has side effects, or
                    // b) The LHS has side effects, or
                    // c) A name on the LHS will exist beyond the life of this statement.
                    if (checkForStateChangeHelper(n.getFirstChild(), checkForNewObjects, compiler) || checkForStateChangeHelper(n.getLastChild(), checkForNewObjects, compiler)) {
                        return true;
                    }
                    if (isGet(assignTarget)) {
                        // If the object being assigned to is a local object, don't
                        // consider this a side-effect as it can't be referenced
                        // elsewhere.  Don't do this recursively as the property might
                        // be an alias of another object, unlike a literal below.
                        Node current = assignTarget.getFirstChild();
                        if (evaluatesToLocalValue(current)) {
                            return false;
                        }
                        // A literal value as defined by "isLiteralValue" is guaranteed
                        // not to be an alias, or any components which are aliases of
                        // other objects.
                        // If the root object is a literal don't consider this a
                        // side-effect.
                        while (isGet(current)) {
                            current = current.getFirstChild();
                        }
                        return !isLiteralValue(current, true);
                    } else {
                        // TODO(johnlenz): remove this code and make this an exception. This
                        // is here only for legacy reasons, the AST is not valid but
                        // preserve existing behavior.
                        return !isLiteralValue(assignTarget, true);
                    }
                }
                return true;
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            if (checkForStateChangeHelper(c, checkForNewObjects, compiler)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Do calls to this constructor have side effects?
     *
     * @param callNode - constructor call node
     */
    static boolean constructorCallHasSideEffects(Node callNode) {
        return constructorCallHasSideEffects(callNode, null);
    }

    static boolean constructorCallHasSideEffects(Node callNode, AbstractCompiler compiler) {
        if (!callNode.isNew()) {
            throw new IllegalStateException("Expected NEW node, got " + Token.name(callNode.getType()));
        }
        if (callNode.isNoSideEffectsCall()) {
            return false;
        }
        Node nameNode = callNode.getFirstChild();
        if (nameNode.isName() && CONSTRUCTORS_WITHOUT_SIDE_EFFECTS.contains(nameNode.getString())) {
            return false;
        }
        return true;
    }

    // A list of built-in object creation or primitive type cast functions that
    // can also be called as constructors but lack side-effects.
    // TODO(johnlenz): consider adding an extern annotation for this.
    private static final Set<String> BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS = ImmutableSet.of("Object", "Array", "String", "Number", "Boolean", "RegExp", "Error");

    private static final Set<String> OBJECT_METHODS_WITHOUT_SIDEEFFECTS = ImmutableSet.of("toString", "valueOf");

    private static final Set<String> REGEXP_METHODS = ImmutableSet.of("test", "exec");

    private static final Set<String> STRING_REGEXP_METHODS = ImmutableSet.of("match", "replace", "search", "split");

    /**
     * Returns true if calls to this function have side effects.
     *
     * @param callNode The call node to inspected.
     * @param compiler A compiler object to provide program state changing
     *     context information. Can be null.
     */
    static boolean functionCallHasSideEffects(Node callNode, @Nullable AbstractCompiler compiler) {
        if (!callNode.isCall()) {
            throw new IllegalStateException("Expected CALL node, got " + Token.name(callNode.getType()));
        }
        if (callNode.isNoSideEffectsCall()) {
            return false;
        }
        Node nameNode = callNode.getFirstChild();
        // Built-in functions with no side effects.
        if (nameNode.isName()) {
            String name = nameNode.getString();
            if (BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS.contains(name)) {
                return false;
            }
        } else if (nameNode.isGetProp()) {
            if (callNode.hasOneChild() && OBJECT_METHODS_WITHOUT_SIDEEFFECTS.contains(nameNode.getLastChild().getString())) {
                return false;
            }
            if (callNode.isOnlyModifiesThisCall() && evaluatesToLocalValue(nameNode.getFirstChild())) {
                return false;
            }
            // Math.floor has no side-effects.
            // TODO(nicksantos): This is a terrible terrible hack, until
            // I create a definitionProvider that understands namespacing.
            if (nameNode.getFirstChild().isName()) {
                if ("Math.floor".equals(nameNode.getQualifiedName())) {
                    return false;
                }
            }
            if (compiler != null && !compiler.hasRegExpGlobalReferences()) {
                if (nameNode.getFirstChild().isRegExp() && REGEXP_METHODS.contains(nameNode.getLastChild().getString())) {
                    return false;
                } else if (nameNode.getFirstChild().isString() && STRING_REGEXP_METHODS.contains(nameNode.getLastChild().getString())) {
                    Node param = nameNode.getNext();
                    if (param != null && (param.isString() || param.isRegExp()))
                        return false;
                }
            }
        }
        return true;
    }

    /**
     * @return Whether the call has a local result.
     */
    static boolean callHasLocalResult(Node n) {
        Preconditions.checkState(n.isCall());
        return (n.getSideEffectFlags() & Node.FLAG_LOCAL_RESULTS) > 0;
    }

    /**
     * @return Whether the new has a local result.
     */
    static boolean newHasLocalResult(Node n) {
        Preconditions.checkState(n.isNew());
        return n.isOnlyModifiesThisCall();
    }

    /*
   *  0 comma ,
   *  1 assignment = += -= *= /= %= <<= >>= >>>= &= ^= |=
   *  2 conditional ?:
   *  3 logical-or ||
   *  4 logical-and &&
   *  5 bitwise-or |
   *  6 bitwise-xor ^
   *  7 bitwise-and &
   *  8 equality == !=
   *  9 relational < <= > >=
   * 10 bitwise shift << >> >>>
   * 11 addition/subtraction + -
   * 12 multiply/divide * / %
   * 13 negation/increment ! ~ - ++ --
   * 14 call, member () [] .
   */
    static int precedence(int type) {
        switch(type) {
            case Token.COMMA:
                return 0;
            case Token.ASSIGN_BITOR:
            case Token.ASSIGN_BITXOR:
            case Token.ASSIGN_BITAND:
            case Token.ASSIGN_LSH:
            case Token.ASSIGN_RSH:
            case Token.ASSIGN_URSH:
            case Token.ASSIGN_ADD:
            case Token.ASSIGN_SUB:
            case Token.ASSIGN_MUL:
            case Token.ASSIGN_DIV:
            case Token.ASSIGN_MOD:
            case Token.ASSIGN:
                return 1;
            // ?: operator
            case Token.HOOK:
                return 2;
            case Token.OR:
                return 3;
            case Token.AND:
                return 4;
            case Token.BITOR:
                return 5;
            case Token.BITXOR:
                return 6;
            case Token.BITAND:
                return 7;
            case Token.EQ:
            case Token.NE:
            case Token.SHEQ:
            case Token.SHNE:
                return 8;
            case Token.LT:
            case Token.GT:
            case Token.LE:
            case Token.GE:
            case Token.INSTANCEOF:
            case Token.IN:
                return 9;
            case Token.LSH:
            case Token.RSH:
            case Token.URSH:
                return 10;
            case Token.SUB:
            case Token.ADD:
                return 11;
            case Token.MUL:
            case Token.MOD:
            case Token.DIV:
                return 12;
            case Token.INC:
            case Token.DEC:
            case Token.NEW:
            case Token.DELPROP:
            case Token.TYPEOF:
            case Token.VOID:
            case Token.NOT:
            case Token.BITNOT:
            case Token.POS:
            case Token.NEG:
                return 13;
            case Token.CALL:
            case Token.GETELEM:
            case Token.GETPROP:
            // Data values
            case Token.ARRAYLIT:
            // TODO(johnlenz): remove this.
            case Token.EMPTY:
            case Token.FALSE:
            case Token.FUNCTION:
            case Token.NAME:
            case Token.NULL:
            case Token.NUMBER:
            case Token.OBJECTLIT:
            case Token.REGEXP:
            case Token.STRING:
            case Token.STRING_KEY:
            case Token.THIS:
            case Token.TRUE:
                return 15;
            default:
                throw new Error("Unknown precedence for " + Token.name(type) + " (type " + type + ")");
        }
    }

    /**
     * Apply the supplied predicate against
     * all possible result Nodes of the expression.
     */
    static boolean valueCheck(Node n, Predicate<Node> p) {
        switch(n.getType()) {
            case Token.ASSIGN:
            case Token.COMMA:
                return valueCheck(n.getLastChild(), p);
            case Token.AND:
            case Token.OR:
                return valueCheck(n.getFirstChild(), p) && valueCheck(n.getLastChild(), p);
            case Token.HOOK:
                return valueCheck(n.getFirstChild().getNext(), p) && valueCheck(n.getLastChild(), p);
            default:
                return p.apply(n);
        }
    }

    static class NumbericResultPredicate implements Predicate<Node> {

        @Override
        public boolean apply(Node n) {
            return isNumericResultHelper(n);
        }
    }

    static final NumbericResultPredicate NUMBERIC_RESULT_PREDICATE = new NumbericResultPredicate();

    /**
     * Returns true if the result of node evaluation is always a number
     */
    static boolean isNumericResult(Node n) {
        return valueCheck(n, NUMBERIC_RESULT_PREDICATE);
    }

    static boolean isNumericResultHelper(Node n) {
        switch(n.getType()) {
            case Token.ADD:
                return !mayBeString(n.getFirstChild()) && !mayBeString(n.getLastChild());
            case Token.BITNOT:
            case Token.BITOR:
            case Token.BITXOR:
            case Token.BITAND:
            case Token.LSH:
            case Token.RSH:
            case Token.URSH:
            case Token.SUB:
            case Token.MUL:
            case Token.MOD:
            case Token.DIV:
            case Token.INC:
            case Token.DEC:
            case Token.POS:
            case Token.NEG:
            case Token.NUMBER:
                return true;
            case Token.NAME:
                String name = n.getString();
                if (name.equals("NaN")) {
                    return true;
                }
                if (name.equals("Infinity")) {
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    static class BooleanResultPredicate implements Predicate<Node> {

        @Override
        public boolean apply(Node n) {
            return isBooleanResultHelper(n);
        }
    }

    static final BooleanResultPredicate BOOLEAN_RESULT_PREDICATE = new BooleanResultPredicate();

    /**
     * @return Whether the result of node evaluation is always a boolean
     */
    static boolean isBooleanResult(Node n) {
        return valueCheck(n, BOOLEAN_RESULT_PREDICATE);
    }

    static boolean isBooleanResultHelper(Node n) {
        switch(n.getType()) {
            // Primitives
            case Token.TRUE:
            case Token.FALSE:
            // Comparisons
            case Token.EQ:
            case Token.NE:
            case Token.SHEQ:
            case Token.SHNE:
            case Token.LT:
            case Token.GT:
            case Token.LE:
            case Token.GE:
            // Queries
            case Token.IN:
            case Token.INSTANCEOF:
            // Inversion
            case Token.NOT:
            // delete operator returns a boolean.
            case Token.DELPROP:
                return true;
            default:
                return false;
        }
    }

    static boolean isUndefined(Node n) {
        switch(n.getType()) {
            case Token.VOID:
                return true;
            case Token.NAME:
                return n.getString().equals("undefined");
        }
        return false;
    }

    static boolean isNullOrUndefined(Node n) {
        return n.isNull() || isUndefined(n);
    }

    static class MayBeStringResultPredicate implements Predicate<Node> {

        @Override
        public boolean apply(Node n) {
            return mayBeStringHelper(n);
        }
    }

    static final MayBeStringResultPredicate MAY_BE_STRING_PREDICATE = new MayBeStringResultPredicate();

    /**
     * @returns Whether the results is possibly a string.
     */
    static boolean mayBeString(Node n) {
        return mayBeString(n, true);
    }

    static boolean mayBeString(Node n, boolean recurse) {
        if (recurse) {
            return valueCheck(n, MAY_BE_STRING_PREDICATE);
        } else {
            return mayBeStringHelper(n);
        }
    }

    static boolean mayBeStringHelper(Node n) {
        return !isNumericResult(n) && !isBooleanResult(n) && !isUndefined(n) && !n.isNull();
    }

    /**
     * Returns true if the operator is associative.
     * e.g. (a * b) * c = a * (b * c)
     * Note: "+" is not associative because it is also the concatenation
     * for strings. e.g. "a" + (1 + 2) is not "a" + 1 + 2
     */
    static boolean isAssociative(int type) {
        switch(type) {
            case Token.MUL:
            case Token.AND:
            case Token.OR:
            case Token.BITOR:
            case Token.BITXOR:
            case Token.BITAND:
                return true;
            default:
                return false;
        }
    }

    static boolean isAssignmentOp(Node n) {
        switch(n.getType()) {
            case Token.ASSIGN:
            case Token.ASSIGN_BITOR:
            case Token.ASSIGN_BITXOR:
            case Token.ASSIGN_BITAND:
            case Token.ASSIGN_LSH:
            case Token.ASSIGN_RSH:
            case Token.ASSIGN_URSH:
            case Token.ASSIGN_ADD:
            case Token.ASSIGN_SUB:
            case Token.ASSIGN_MUL:
            case Token.ASSIGN_DIV:
            case Token.ASSIGN_MOD:
                return true;
        }
        return false;
    }

    /**
     * Is this a GETPROP or GETELEM node?
     */
    static boolean isGet(Node n) {
        return n.isGetProp() || n.isGetElem();
    }

    /**
     * Is this node the name of a variable being declared?
     *
     * @param n The node
     * @return True if {@code n} is NAME and {@code parent} is VAR
     */
    static boolean isVarDeclaration(Node n) {
        // There is no need to verify that parent != null because a NAME node
        // always has a parent in a valid parse tree.
        return n.isName() && n.getParent().isVar();
    }

    /**
     * Is this node a call expression statement?
     *
     * @param n The node
     * @return True if {@code n} is EXPR_RESULT and {@code n}'s
     *     first child is CALL
     */
    static boolean isExprCall(Node n) {
        return n.isExprResult() && n.getFirstChild().isCall();
    }

    /**
     * @return Whether the node represents a FOR-IN loop.
     */
    static boolean isForIn(Node n) {
        return n.isFor() && n.getChildCount() == 3;
    }

    /**
     * Determines whether the given node is a FOR, DO, WHILE, WITH, or IF node.
     */
    static boolean isControlStructure(Node n) {
        switch(n.getType()) {
            case Token.FOR:
            case Token.DO:
            case Token.WHILE:
            case Token.WITH:
            case Token.IF:
            case Token.LABEL:
            case Token.TRY:
            case Token.CATCH:
            case Token.SWITCH:
            case Token.CASE:
            case Token.DEFAULT_CASE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Determines whether the given node is code node for FOR, DO,
     * WHILE, WITH, or IF node.
     */
    static boolean isControlStructureCodeBlock(Node parent, Node n) {
        switch(parent.getType()) {
            case Token.FOR:
            case Token.WHILE:
            case Token.LABEL:
            case Token.WITH:
                return parent.getLastChild() == n;
            case Token.DO:
                return parent.getFirstChild() == n;
            case Token.IF:
                return parent.getFirstChild() != n;
            case Token.TRY:
                return parent.getFirstChild() == n || parent.getLastChild() == n;
            case Token.CATCH:
                return parent.getLastChild() == n;
            case Token.SWITCH:
            case Token.CASE:
                return parent.getFirstChild() != n;
            case Token.DEFAULT_CASE:
                return true;
            default:
                Preconditions.checkState(isControlStructure(parent));
                return false;
        }
    }

    /**
     * Gets the condition of an ON_TRUE / ON_FALSE CFG edge.
     * @param n a node with an outgoing conditional CFG edge
     * @return the condition node or null if the condition is not obviously a node
     */
    static Node getConditionExpression(Node n) {
        switch(n.getType()) {
            case Token.IF:
            case Token.WHILE:
                return n.getFirstChild();
            case Token.DO:
                return n.getLastChild();
            case Token.FOR:
                switch(n.getChildCount()) {
                    case 3:
                        return null;
                    case 4:
                        return n.getFirstChild().getNext();
                }
                throw new IllegalArgumentException("malformed 'for' statement " + n);
            case Token.CASE:
                return null;
        }
        throw new IllegalArgumentException(n + " does not have a condition.");
    }

    /**
     * @return Whether the node is of a type that contain other statements.
     */
    static boolean isStatementBlock(Node n) {
        return n.isScript() || n.isBlock();
    }

    /**
     * @return Whether the node is used as a statement.
     */
    static boolean isStatement(Node n) {
        return isStatementParent(n.getParent());
    }

    static boolean isStatementParent(Node parent) {
        // It is not possible to determine definitely if a node is a statement
        // or not if it is not part of the AST.  A FUNCTION node can be
        // either part of an expression or a statement.
        Preconditions.checkState(parent != null);
        switch(parent.getType()) {
            case Token.SCRIPT:
            case Token.BLOCK:
            case Token.LABEL:
                return true;
            default:
                return false;
        }
    }

    /**
     * Is this node a function declaration? A function declaration is a function
     * that has a name that is added to the current scope (i.e. a function that
     * is not part of a expression; see {@link #isFunctionExpression}).
     */
    static boolean isFunctionDeclaration(Node n) {
        return n.isFunction() && isStatement(n);
    }

    /**
     * Is this node a hoisted function declaration? A function declaration in the
     * scope root is hoisted to the top of the scope.
     * See {@link #isFunctionDeclaration}).
     */
    static boolean isHoistedFunctionDeclaration(Node n) {
        return isFunctionDeclaration(n) && (n.getParent().isScript() || n.getParent().getParent().isFunction());
    }

    /**
     * Is a FUNCTION node an function expression? An function expression is one
     * that has either no name or a name that is not added to the current scope.
     *
     * <p>Some examples of function expressions:
     * <pre>
     * (function () {})
     * (function f() {})()
     * [ function f() {} ]
     * var f = function f() {};
     * for (function f() {};;) {}
     * </pre>
     *
     * <p>Some examples of functions that are <em>not</em> expressions:
     * <pre>
     * function f() {}
     * if (x); else function f() {}
     * for (;;) { function f() {} }
     * </pre>
     *
     * @param n A node
     * @return Whether n is an function used within an expression.
     */
    static boolean isFunctionExpression(Node n) {
        return n.isFunction() && !isStatement(n);
    }

    /**
     * Returns whether this is a bleeding function (an anonymous named function
     * that bleeds into the inner scope).
     */
    static boolean isBleedingFunctionName(Node n) {
        return n.isName() && !n.getString().isEmpty() && isFunctionExpression(n.getParent());
    }

    /**
     * Determines whether this node is used as an L-value. Notice that sometimes
     * names are used as both L-values and R-values.
     *
     * We treat "var x;" as a pseudo-L-value, which kind of makes sense if you
     * treat it as "assignment to 'undefined' at the top of the scope". But if
     * we're honest with ourselves, it doesn't make sense, and we only do this
     * because it makes sense to treat this as syntactically similar to
     * "var x = 0;".
     *
     * @param n The node
     * @return True if n is an L-value.
     */
    static boolean isLValue(Node n) {
        Preconditions.checkArgument(n.isName() || n.isGetProp() || n.isGetElem());
        Node parent = n.getParent();
        return (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n) || (NodeUtil.isForIn(parent) && parent.getFirstChild() == n) || parent.isVar() || (parent.isFunction() && parent.getFirstChild() == n) || parent.isDec() || parent.isInc() || parent.isParamList() || parent.isCatch();
    }

    /**
     * Determines whether a node represents an object literal key
     * (e.g. key1 in {key1: value1, key2: value2}).
     *
     * @param node A node
     * @param parent The node's parent
     */
    static boolean isObjectLitKey(Node node, Node parent) {
        switch(node.getType()) {
            case Token.STRING_KEY:
            case Token.GETTER_DEF:
            case Token.SETTER_DEF:
                return true;
        }
        return false;
    }

    /**
     * Get the name of an object literal key.
     *
     * @param key A node
     */
    static String getObjectLitKeyName(Node key) {
        switch(key.getType()) {
            case Token.STRING_KEY:
            case Token.GETTER_DEF:
            case Token.SETTER_DEF:
                return key.getString();
        }
        throw new IllegalStateException("Unexpected node type: " + key);
    }

    /**
     * @param key A OBJECTLIT key node.
     * @return The type expected when using the key.
     */
    static JSType getObjectLitKeyTypeFromValueType(Node key, JSType valueType) {
        if (valueType != null) {
            switch(key.getType()) {
                case Token.GETTER_DEF:
                    // GET must always return a function type.
                    if (valueType.isFunctionType()) {
                        FunctionType fntype = valueType.toMaybeFunctionType();
                        valueType = fntype.getReturnType();
                    } else {
                        return null;
                    }
                    break;
                case Token.SETTER_DEF:
                    if (valueType.isFunctionType()) {
                        // SET must always return a function type.
                        FunctionType fntype = valueType.toMaybeFunctionType();
                        Node param = fntype.getParametersNode().getFirstChild();
                        // SET function must always have one parameter.
                        valueType = param.getJSType();
                    } else {
                        return null;
                    }
                    break;
            }
        }
        return valueType;
    }

    /**
     * Converts an operator's token value (see {@link Token}) to a string
     * representation.
     *
     * @param operator the operator's token value to convert
     * @return the string representation or {@code null} if the token value is
     * not an operator
     */
    static String opToStr(int operator) {
        switch(operator) {
            case Token.BITOR:
                return "|";
            case Token.OR:
                return "||";
            case Token.BITXOR:
                return "^";
            case Token.AND:
                return "&&";
            case Token.BITAND:
                return "&";
            case Token.SHEQ:
                return "===";
            case Token.EQ:
                return "==";
            case Token.NOT:
                return "!";
            case Token.NE:
                return "!=";
            case Token.SHNE:
                return "!==";
            case Token.LSH:
                return "<<";
            case Token.IN:
                return "in";
            case Token.LE:
                return "<=";
            case Token.LT:
                return "<";
            case Token.URSH:
                return ">>>";
            case Token.RSH:
                return ">>";
            case Token.GE:
                return ">=";
            case Token.GT:
                return ">";
            case Token.MUL:
                return "*";
            case Token.DIV:
                return "/";
            case Token.MOD:
                return "%";
            case Token.BITNOT:
                return "~";
            case Token.ADD:
                return "+";
            case Token.SUB:
                return "-";
            case Token.POS:
                return "+";
            case Token.NEG:
                return "-";
            case Token.ASSIGN:
                return "=";
            case Token.ASSIGN_BITOR:
                return "|=";
            case Token.ASSIGN_BITXOR:
                return "^=";
            case Token.ASSIGN_BITAND:
                return "&=";
            case Token.ASSIGN_LSH:
                return "<<=";
            case Token.ASSIGN_RSH:
                return ">>=";
            case Token.ASSIGN_URSH:
                return ">>>=";
            case Token.ASSIGN_ADD:
                return "+=";
            case Token.ASSIGN_SUB:
                return "-=";
            case Token.ASSIGN_MUL:
                return "*=";
            case Token.ASSIGN_DIV:
                return "/=";
            case Token.ASSIGN_MOD:
                return "%=";
            case Token.VOID:
                return "void";
            case Token.TYPEOF:
                return "typeof";
            case Token.INSTANCEOF:
                return "instanceof";
            default:
                return null;
        }
    }

    /**
     * Converts an operator's token value (see {@link Token}) to a string
     * representation or fails.
     *
     * @param operator the operator's token value to convert
     * @return the string representation
     * @throws Error if the token value is not an operator
     */
    static String opToStrNoFail(int operator) {
        String res = opToStr(operator);
        if (res == null) {
            throw new Error("Unknown op " + operator + ": " + Token.name(operator));
        }
        return res;
    }

    /**
     * @return true if n or any of its children are of the specified type
     */
    static boolean containsType(Node node, int type, Predicate<Node> traverseChildrenPred) {
        return has(node, new MatchNodeType(type), traverseChildrenPred);
    }

    /**
     * Gets the root node of a qualified name. Must be either NAME or THIS.
     */
    public static Node getRootOfQualifiedName(Node qName) {
        for (Node current = qName; true; current = current.getFirstChild()) {
            if (current.isName() || current.isThis()) {
                return current;
            }
            Preconditions.checkState(current.isGetProp());
        }
    }

    /**
     * Test if all characters in the string are in the Basic Latin (aka ASCII)
     * character set - that they have UTF-16 values equal to or below 0x7f.
     * This check can find which identifiers with Unicode characters need to be
     * escaped in order to allow resulting files to be processed by non-Unicode
     * aware UNIX tools and editors.
     * *
     * See http://en.wikipedia.org/wiki/Latin_characters_in_Unicode
     * for more on Basic Latin.
     *
     * @param s The string to be checked for ASCII-goodness.
     *
     * @return True if all characters in the string are in Basic Latin set.
     */
    static boolean isLatin(String s) {
        char LARGEST_BASIC_LATIN = 0x7f;
        int len = s.length();
        for (int index = 0; index < len; index++) {
            char c = s.charAt(index);
            if (c > LARGEST_BASIC_LATIN) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines whether the given name is a valid qualified name.
     */
    // TODO(nicksantos): This should be moved into a "Language" API,
    /**
     * A predicate for matching nodes with the specified type.
     */
    static class MatchNodeType implements Predicate<Node> {

        final int type;

        MatchNodeType(int type) {
            this.type = type;
        }

        @Override
        public boolean apply(Node n) {
            return n.getType() == type;
        }
    }

    /**
     * A predicate for matching anything except function nodes.
     */
    private static class MatchNotFunction implements Predicate<Node> {

        @Override
        public boolean apply(Node n) {
            return !n.isFunction();
        }
    }

    static final Predicate<Node> MATCH_NOT_FUNCTION = new MatchNotFunction();

    /**
     * @return Whether the predicate is true for the node or any of its children.
     */
    static boolean has(Node node, Predicate<Node> pred, Predicate<Node> traverseChildrenPred) {
        if (pred.apply(node)) {
            return true;
        }
        if (!traverseChildrenPred.apply(node)) {
            return false;
        }
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
            if (has(c, pred, traverseChildrenPred)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return Whether a TRY node has a finally block.
     */
    static boolean hasFinally(Node n) {
        Preconditions.checkArgument(n.isTry());
        return n.getChildCount() == 3;
    }

    /**
     * @return The BLOCK node containing the CATCH node (if any)
     * of a TRY.
     */
    static Node getCatchBlock(Node n) {
        Preconditions.checkArgument(n.isTry());
        return n.getFirstChild().getNext();
    }

    /**
     * @return Whether BLOCK (from a TRY node) contains a CATCH.
     * @see NodeUtil#getCatchBlock
     */
    static boolean hasCatchHandler(Node n) {
        Preconditions.checkArgument(n.isBlock());
        return n.hasChildren() && n.getFirstChild().isCatch();
    }

    /**
     * @param fnNode The function.
     * @return The Node containing the Function parameters.
     */
    public static Node getFunctionParameters(Node fnNode) {
        // Function NODE: [ FUNCTION -> NAME, LP -> ARG1, ARG2, ... ]
        Preconditions.checkArgument(fnNode.isFunction());
        return fnNode.getFirstChild().getNext();
    }

    /**
     * Returns true if a name node represents a constant variable.
     *
     * <p>Determining whether a variable is constant has three steps:
     * <ol>
     * <li>In CodingConventionAnnotator, any name that matches the
     *     {@link CodingConvention#isConstant(String)} is annotated with an
     *     IS_CONSTANT_NAME property.
     * <li>The normalize pass renames any variable with the IS_CONSTANT_NAME
     *     annotation and that is initialized to a constant value with
     *     a variable name including $$constant.
     * <li>Return true here if the variable includes $$constant in its name.
     * </ol>
     *
     * @param node A NAME or STRING node
     * @return True if the variable is constant
     */
    static boolean isConstantName(Node node) {
        return node.getBooleanProp(Node.IS_CONSTANT_NAME);
    }

    /**
     * Get the JSDocInfo for a function.
     */
    public static JSDocInfo getFunctionJSDocInfo(Node n) {
        Preconditions.checkState(n.isFunction());
        JSDocInfo fnInfo = n.getJSDocInfo();
        if (fnInfo == null && NodeUtil.isFunctionExpression(n)) {
            // Look for the info on other nodes.
            Node parent = n.getParent();
            if (parent.isAssign()) {
                // on ASSIGNs
                fnInfo = parent.getJSDocInfo();
            } else if (parent.isName()) {
                // on var NAME = function() { ... };
                fnInfo = parent.getParent().getJSDocInfo();
            }
        }
        return fnInfo;
    }

    /**
     * @param n The node.
     * @return The source name property on the node or its ancestors.
     */
    public static String getSourceName(Node n) {
        String sourceName = null;
        while (sourceName == null && n != null) {
            sourceName = n.getSourceFileName();
            n = n.getParent();
        }
        return sourceName;
    }

    /**
     * @param n The node.
     * @return The InputId property on the node or its ancestors.
     */
    public static InputId getInputId(Node n) {
        while (n != null && !n.isScript()) {
            n = n.getParent();
        }
        return (n != null && n.isScript()) ? n.getInputId() : null;
    }

    /**
     * @return Whether the node is known to be a value that is not referenced
     * elsewhere.
     */
    static boolean evaluatesToLocalValue(Node value) {
        return evaluatesToLocalValue(value, Predicates.<Node>alwaysFalse());
    }

    /**
     * @param locals A predicate to apply to unknown local values.
     * @return Whether the node is known to be a value that is not a reference
     *     outside the expression scope.
     */
    static boolean evaluatesToLocalValue(Node value, Predicate<Node> locals) {
        switch(value.getType()) {
            case Token.ASSIGN:
                // A result that is aliased by a non-local name, is the effectively the
                // same as returning a non-local name, but this doesn't matter if the
                // value is immutable.
                return NodeUtil.isImmutableValue(value.getLastChild()) || (locals.apply(value) && evaluatesToLocalValue(value.getLastChild(), locals));
            case Token.COMMA:
                return evaluatesToLocalValue(value.getLastChild(), locals);
            case Token.AND:
            case Token.OR:
                return evaluatesToLocalValue(value.getFirstChild(), locals) && evaluatesToLocalValue(value.getLastChild(), locals);
            case Token.HOOK:
                return evaluatesToLocalValue(value.getFirstChild().getNext(), locals) && evaluatesToLocalValue(value.getLastChild(), locals);
            case Token.INC:
            case Token.DEC:
                if (value.getBooleanProp(Node.INCRDECR_PROP)) {
                    return evaluatesToLocalValue(value.getFirstChild(), locals);
                } else {
                    return true;
                }
            case Token.THIS:
                return locals.apply(value);
            case Token.NAME:
                return isImmutableValue(value) || locals.apply(value);
            case Token.GETELEM:
            case Token.GETPROP:
                // There is no information about the locality of object properties.
                return locals.apply(value);
            case Token.CALL:
                return callHasLocalResult(value) || isToStringMethodCall(value) || locals.apply(value);
            case Token.NEW:
                return newHasLocalResult(value) || locals.apply(value);
            case Token.FUNCTION:
            case Token.REGEXP:
            case Token.ARRAYLIT:
            case Token.OBJECTLIT:
                // Literals objects with non-literal children are allowed.
                return true;
            case Token.DELPROP:
            case Token.IN:
                // TODO(johnlenz): should IN operator be included in #isSimpleOperator?
                return true;
            default:
                // Other op force a local value:
                //  x = '' + g (x is now an local string)
                //  x -= g (x is now an local number)
                if (isAssignmentOp(value) || isSimpleOperator(value) || isImmutableValue(value)) {
                    return true;
                }
                throw new IllegalStateException("Unexpected expression node" + value + "\n parent:" + value.getParent());
        }
    }

    private static boolean isToStringMethodCall(Node call) {
        Node getNode = call.getFirstChild();
        if (isGet(getNode)) {
            Node propNode = getNode.getLastChild();
            return propNode.isString() && "toString".equals(propNode.getString());
        }
        return false;
    }

    /**
     * Find the best JSDoc for the given node.
     */
    static JSDocInfo getBestJSDocInfo(Node n) {
        JSDocInfo info = n.getJSDocInfo();
        if (info == null) {
            Node parent = n.getParent();
            if (parent == null) {
                return null;
            }
            if (parent.isName()) {
                return getBestJSDocInfo(parent);
            } else if (parent.isAssign()) {
                return parent.getJSDocInfo();
            } else if (isObjectLitKey(parent, parent.getParent())) {
                return parent.getJSDocInfo();
            } else if (parent.isFunction()) {
                return parent.getJSDocInfo();
            } else if (parent.isVar() && parent.hasOneChild()) {
                return parent.getJSDocInfo();
            } else if ((parent.isHook() && parent.getFirstChild() != n) || parent.isOr() || parent.isAnd() || (parent.isComma() && parent.getFirstChild() != n)) {
                return getBestJSDocInfo(parent);
            }
        }
        return info;
    }

    /**
     * Find the l-value that the given r-value is being assigned to.
     */
    static Node getBestLValue(Node n) {
        Node parent = n.getParent();
        boolean isFunctionDeclaration = isFunctionDeclaration(n);
        if (isFunctionDeclaration) {
            return n.getFirstChild();
        } else if (parent.isName()) {
            return parent;
        } else if (parent.isAssign()) {
            return parent.getFirstChild();
        } else if (isObjectLitKey(parent, parent.getParent())) {
            return parent;
        } else if ((parent.isHook() && parent.getFirstChild() != n) || parent.isOr() || parent.isAnd() || (parent.isComma() && parent.getFirstChild() != n)) {
            return getBestLValue(parent);
        }
        return null;
    }

    /**
     * Get the owner of the given l-value node.
     */
    static Node getBestLValueOwner(@Nullable Node lValue) {
        if (lValue == null || lValue.getParent() == null) {
            return null;
        }
        if (isObjectLitKey(lValue, lValue.getParent())) {
            return getBestLValue(lValue.getParent());
        } else if (isGet(lValue)) {
            return lValue.getFirstChild();
        }
        return null;
    }

    /**
     * Get the name of the given l-value node.
     */
    static String getBestLValueName(@Nullable Node lValue) {
        if (lValue == null || lValue.getParent() == null) {
            return null;
        }
        if (isObjectLitKey(lValue, lValue.getParent())) {
            Node owner = getBestLValue(lValue.getParent());
            if (owner != null) {
                String ownerName = getBestLValueName(owner);
                if (ownerName != null) {
                    return ownerName + "." + getObjectLitKeyName(lValue);
                }
            }
            return null;
        }
        return lValue.getQualifiedName();
    }
}
