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
 *   Norris Boyd
 *   Roger Lawrence
 *   Mike McCabe
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
package com.google.javascript.rhino;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.jstype.JSType;
import java.io.Serializable;
import java.util.Arrays;

/**
 * This class implements the root of the intermediate representation.
 */
public class Node implements Cloneable, Serializable {

    private static final long serialVersionUID = 1L;

    public static final int // Rhino's AST captures data flow. These are the annotations
    // it used. We've mostly torn them out.
    LOCAL_BLOCK_PROP = -3, OBJECT_IDS_PROP = -2, CATCH_SCOPE_PROP = -1, LABEL_ID_PROP = 0, TARGET_PROP = 1, BREAK_PROP = 2, CONTINUE_PROP = 3, ENUM_PROP = 4, FUNCTION_PROP = 5, TEMP_PROP = 6, LOCAL_PROP = 7, CODEOFFSET_PROP = 8, FIXUPS_PROP = 9, VARS_PROP = 10, USES_PROP = 11, REGEXP_PROP = 12, CASES_PROP = 13, DEFAULT_PROP = 14, CASEARRAY_PROP = 15, SOURCENAME_PROP = 16, TYPE_PROP = 17, SPECIAL_PROP_PROP = 18, LABEL_PROP = 19, FINALLY_PROP = 20, LOCALCOUNT_PROP = 21, /*
        the following properties are defined and manipulated by the
        optimizer -
        TARGETBLOCK_PROP - the block referenced by a branch node
        VARIABLE_PROP - the variable referenced by a BIND or NAME node
        LASTUSE_PROP - that variable node is the last reference before
                        a new def or the end of the block
        ISNUMBER_PROP - this node generates code on Number children and
                        delivers a Number result (as opposed to Objects)
        DIRECTCALL_PROP - this call node should emit code to test the function
                          object against the known class and call diret if it
                          matches.
    */
    // contains a TokenStream.JSDocInfo object
    // contains a TokenStream.JSDocInfo object
    TARGETBLOCK_PROP = 22, // contains a TokenStream.JSDocInfo object
    VARIABLE_PROP = 23, // contains a TokenStream.JSDocInfo object
    LASTUSE_PROP = 24, // contains a TokenStream.JSDocInfo object
    ISNUMBER_PROP = 25, // contains a TokenStream.JSDocInfo object
    DIRECTCALL_PROP = 26, // contains a TokenStream.JSDocInfo object
    SPECIALCALL_PROP = 27, // the name node is a variable length
    DEBUGSOURCE_PROP = 28, // argument placeholder. It can never be
    JSDOC_INFO_PROP = 29, // used in conjunction with JSDOC_INFO_PROP.
    VAR_ARGS_NAME = 29, // array of skipped indexes of array literal
    // pre or post type of increment/decrement
    // type of element access operation
    SKIP_INDEXES_PROP = 30, // property name
    INCRDECR_PROP = 31, // expression is parenthesized
    MEMBER_TYPE_PROP = 32, // set to indicate a quoted object lit key
    NAME_PROP = 33, // The name node is an optional argument.
    PARENTHESIZED_PROP = 34, // A synthetic block. Used to make
    QUOTED_PROP = 35, // processing simpler, and does not
    OPT_ARG_NAME = 36, // represent a real block in the source.
    SYNTHETIC_BLOCK_PROP = 37, // Contains the path of the source file
    // from which the current node was parsed.
    // Used to indicate BLOCK that replaced
    SOURCEFILE_PROP = 38, // EMPTY nodes.
    // The original name of the node, before
    EMPTY_BLOCK = 39, // renaming.
    // The type syntax without curly braces.
    ORIGINALNAME_PROP = 40, // Function or constructor call has no
    // side effects.
    BRACELESS_TYPE = 41, // Coding convention props
    NO_SIDE_EFFECTS_CALL = 42, // The variable or property is constant.
    // The parameter is optional.
    // The parameter is a var_args.
    IS_CONSTANT_NAME = 43, // The variable creates a namespace.
    IS_OPTIONAL_PARAM = 44, // The function is a dispatcher function,
    IS_VAR_ARGS_PARAM = 45, // probably generated from Java code, and
    IS_NAMESPACE = 46, // should be resolved to the proper
    IS_DISPATCHER = 47, // overload if possible.
    // The ES5 directives on this node.
    // ES5 distinguishes between direct and
    // indirect calls to eval.
    DIRECTIVES = 48, DIRECT_EVAL = 49, LAST_PROP = 49;

    // values of ISNUMBER_PROP to specify
    // which of the children are Number types
    public static final int BOTH = 0, LEFT = 1, RIGHT = 2;

    public static final int // values for SPECIALCALL_PROP
    NON_SPECIALCALL = 0, SPECIALCALL_EVAL = 1, SPECIALCALL_WITH = 2;

    public static final int // flags for INCRDECR_PROP
    DECR_FLAG = 0x1, POST_FLAG = 0x2;

    public static final int // flags for MEMBER_TYPE_PROP
    // property access: element is valid name
    // x.@y or x..@y
    // x..y or x..@i
    PROPERTY_FLAG = 0x1, ATTRIBUTE_FLAG = 0x2, DESCENDANTS_FLAG = 0x4;

    private static final String propToString(int propType) {
        switch(propType) {
            case LOCAL_BLOCK_PROP:
                return "local_block";
            case OBJECT_IDS_PROP:
                return "object_ids_prop";
            case CATCH_SCOPE_PROP:
                return "catch_scope_prop";
            case LABEL_ID_PROP:
                return "label_id_prop";
            case TARGET_PROP:
                return "target";
            case BREAK_PROP:
                return "break";
            case CONTINUE_PROP:
                return "continue";
            case ENUM_PROP:
                return "enum";
            case FUNCTION_PROP:
                return "function";
            case TEMP_PROP:
                return "temp";
            case LOCAL_PROP:
                return "local";
            case CODEOFFSET_PROP:
                return "codeoffset";
            case FIXUPS_PROP:
                return "fixups";
            case VARS_PROP:
                return "vars";
            case USES_PROP:
                return "uses";
            case REGEXP_PROP:
                return "regexp";
            case CASES_PROP:
                return "cases";
            case DEFAULT_PROP:
                return "default";
            case CASEARRAY_PROP:
                return "casearray";
            case SOURCENAME_PROP:
                return "sourcename";
            case TYPE_PROP:
                return "type";
            case SPECIAL_PROP_PROP:
                return "special_prop";
            case LABEL_PROP:
                return "label";
            case FINALLY_PROP:
                return "finally";
            case LOCALCOUNT_PROP:
                return "localcount";
            case TARGETBLOCK_PROP:
                return "targetblock";
            case VARIABLE_PROP:
                return "variable";
            case LASTUSE_PROP:
                return "lastuse";
            case ISNUMBER_PROP:
                return "isnumber";
            case DIRECTCALL_PROP:
                return "directcall";
            case SPECIALCALL_PROP:
                return "specialcall";
            case DEBUGSOURCE_PROP:
                return "debugsource";
            case JSDOC_INFO_PROP:
                return "jsdoc_info";
            case SKIP_INDEXES_PROP:
                return "skip_indexes";
            case INCRDECR_PROP:
                return "incrdecr";
            case MEMBER_TYPE_PROP:
                return "member_type";
            case NAME_PROP:
                return "name";
            case PARENTHESIZED_PROP:
                return "parenthesized";
            case QUOTED_PROP:
                return "quoted";
            case SYNTHETIC_BLOCK_PROP:
                return "synthetic";
            case SOURCEFILE_PROP:
                return "sourcefile";
            case EMPTY_BLOCK:
                return "empty_block";
            case ORIGINALNAME_PROP:
                return "originalname";
            case NO_SIDE_EFFECTS_CALL:
                return "no_side_effects_call";
            case IS_CONSTANT_NAME:
                return "is_constant_name";
            case IS_OPTIONAL_PARAM:
                return "is_optional_param";
            case IS_VAR_ARGS_PARAM:
                return "is_var_args_param";
            case IS_NAMESPACE:
                return "is_namespace";
            case IS_DISPATCHER:
                return "is_dispatcher";
            case DIRECTIVES:
                return "directives";
            case DIRECT_EVAL:
                return "direct_eval";
            default:
                Kit.codeBug();
        }
        return null;
    }

    private static class StringNode extends Node {

        private static final long serialVersionUID = 1L;

        StringNode(int type, String str) {
            super(type);
            if (null == str) {
                throw new IllegalArgumentException("StringNode: str is null");
            }
            this.str = str;
        }

        StringNode(int type, String str, int lineno, int charno) {
            super(type, lineno, charno);
            throw new AssertionError("This method should not be reached! Signature: StringNode(int, String, int, int)");
        }

        /**
         * returns the string content.
         * @return non null.
         */
        @Override
        public String getString() {
            return this.str;
        }

        private String str;
    }

    private static class PropListItem implements Serializable {

        private static final long serialVersionUID = 1L;

        PropListItem next;

        int type;

        int intValue;

        Object objectValue;
    }

    public Node(int nodeType) {
        type = nodeType;
        parent = null;
        sourcePosition = -1;
    }

    public Node(int nodeType, int lineno, int charno) {
        type = nodeType;
        parent = null;
        sourcePosition = mergeLineCharNo(lineno, charno);
    }

    public static Node newString(int type, String str) {
        return new StringNode(type, str);
    }

    public int getType() {
        return type;
    }

    public Node getFirstChild() {
        return first;
    }

    public Node getLastChild() {
        return last;
    }

    public Node getNext() {
        return next;
    }

    public void addChildToFront(Node child) {
        Preconditions.checkArgument(child.parent == null);
        Preconditions.checkArgument(child.next == null);
        child.parent = this;
        child.next = first;
        first = child;
        if (last == null) {
            last = child;
        }
    }

    public void addChildToBack(Node child) {
        Preconditions.checkArgument(child.parent == null);
        Preconditions.checkArgument(child.next == null);
        child.parent = this;
        child.next = null;
        if (last == null) {
            first = last = child;
            return;
        }
        last.next = child;
        last = child;
    }

    private PropListItem lookupProperty(int propType) {
        PropListItem x = propListHead;
        while (x != null && propType != x.type) {
            x = x.next;
        }
        return x;
    }

    private PropListItem ensureProperty(int propType) {
        PropListItem item = lookupProperty(propType);
        if (item == null) {
            item = new PropListItem();
            item.type = propType;
            item.next = propListHead;
            propListHead = item;
        }
        return item;
    }

    public boolean getBooleanProp(int propType) {
        return getIntProp(propType, 0) != 0;
    }

    public int getIntProp(int propType, int defaultValue) {
        PropListItem item = lookupProperty(propType);
        if (item == null) {
            return defaultValue;
        }
        return item.intValue;
    }

    public void putBooleanProp(int propType, boolean prop) {
        putIntProp(propType, prop ? 1 : 0);
    }

    public void putIntProp(int propType, int prop) {
        PropListItem item = ensureProperty(propType);
        item.intValue = prop;
    }

    // Gets all the property types, in sorted order.
    private int[] getSortedPropTypes() {
        int count = 0;
        for (PropListItem x = propListHead; x != null; x = x.next) {
            count++;
        }
        int[] keys = new int[count];
        for (PropListItem x = propListHead; x != null; x = x.next) {
            count--;
            keys[count] = x.type;
        }
        Arrays.sort(keys);
        return keys;
    }

    public int getLineno() {
        return extractLineno(sourcePosition);
    }

    /**
     * Can only be called when <tt>getType() == TokenStream.NUMBER</tt>
     */
    public double getDouble() throws UnsupportedOperationException {
        if (this.getType() == Token.NUMBER) {
            throw new IllegalStateException("Number node not created with Node.newNumber");
        } else {
            throw new UnsupportedOperationException(this + " is not a number node");
        }
    }

    /**
     * Can only be called when node has String context.
     */
    public String getString() throws UnsupportedOperationException {
        if (this.getType() == Token.STRING) {
            throw new IllegalStateException("String node not created with Node.newString");
        } else {
            throw new UnsupportedOperationException(this + " is not a string node");
        }
    }

    @Override
    public String toString() {
        return toString(true, true, true);
    }

    public String toString(boolean printSource, boolean printAnnotations, boolean printType) {
        if (Token.printTrees) {
            StringBuilder sb = new StringBuilder();
            toString(sb, printSource, printAnnotations, printType);
            return sb.toString();
        }
        return String.valueOf(type);
    }

    private void toString(StringBuilder sb, boolean printSource, boolean printAnnotations, boolean printType) {
        if (Token.printTrees) {
            sb.append(Token.name(type));
            if (this instanceof StringNode) {
                sb.append(' ');
                sb.append(getString());
            } else if (type == Token.FUNCTION) {
                sb.append(' ');
                sb.append(first.getString());
            } else if (this instanceof ScriptOrFnNode) {
                ScriptOrFnNode sof = (ScriptOrFnNode) this;
                if (this instanceof FunctionNode) {
                    FunctionNode fn = (FunctionNode) this;
                    sb.append(' ');
                    sb.append(fn.getFunctionName());
                }
                if (printSource) {
                    sb.append(" [source name: ");
                    sb.append(sof.getSourceName());
                    sb.append("] [encoded source length: ");
                    sb.append(sof.getEncodedSourceEnd() - sof.getEncodedSourceStart());
                    sb.append("] [base line: ");
                    sb.append(sof.getBaseLineno());
                    sb.append("] [end line: ");
                    sb.append(sof.getEndLineno());
                    sb.append(']');
                }
            } else if (type == Token.NUMBER) {
                sb.append(' ');
                sb.append(getDouble());
            }
            if (printSource) {
                int lineno = getLineno();
                if (lineno != -1) {
                    sb.append(' ');
                    sb.append(lineno);
                }
            }
            if (printAnnotations) {
                int[] keys = getSortedPropTypes();
                for (int i = 0; i < keys.length; i++) {
                    int type = keys[i];
                    PropListItem x = lookupProperty(type);
                    sb.append(" [");
                    sb.append(propToString(type));
                    sb.append(": ");
                    String value;
                    switch(type) {
                        case // can't add this as it recurses
                        TARGETBLOCK_PROP:
                            value = "target block property";
                            break;
                        case // can't add this as it is dull
                        LOCAL_BLOCK_PROP:
                            value = "last local block";
                            break;
                        case ISNUMBER_PROP:
                            switch(x.intValue) {
                                case BOTH:
                                    value = "both";
                                    break;
                                case RIGHT:
                                    value = "right";
                                    break;
                                case LEFT:
                                    value = "left";
                                    break;
                                default:
                                    throw Kit.codeBug();
                            }
                            break;
                        case SPECIALCALL_PROP:
                            switch(x.intValue) {
                                case SPECIALCALL_EVAL:
                                    value = "eval";
                                    break;
                                case SPECIALCALL_WITH:
                                    value = "with";
                                    break;
                                default:
                                    // NON_SPECIALCALL should not be stored
                                    throw Kit.codeBug();
                            }
                            break;
                        default:
                            Object obj = x.objectValue;
                            if (obj != null) {
                                value = obj.toString();
                            } else {
                                value = String.valueOf(x.intValue);
                            }
                            break;
                    }
                    sb.append(value);
                    sb.append(']');
                }
            }
            if (printType) {
                if (jsType != null) {
                    String jsTypeString = jsType.toString();
                    if (jsTypeString != null) {
                        sb.append(" : ");
                        sb.append(jsTypeString);
                    }
                }
            }
        }
    }

    // type of the node; Token.NAME for example
    int type;

    // next sibling
    Node next;

    // first element of a linked list of children
    private Node first;

    // last element of a linked list of children
    private Node last;

    /**
     * Linked list of properties. Since vast majority of nodes would have
     * no more then 2 properties, linked list saves memory and provides
     * fast lookup. If this does not holds, propListHead can be replaced
     * by UintMap.
     */
    private PropListItem propListHead;

    /**
     * COLUMN_BITS represents how many of the lower-order bits of
     * sourcePosition are reserved for storing the column number.
     * Bits above these store the line number.
     * This gives us decent position information for everything except
     * files already passed through a minimizer, where lines might
     * be longer than 4096 characters.
     */
    public static final int COLUMN_BITS = 12;

    /**
     * MAX_COLUMN_NUMBER represents the maximum column number that can
     * be represented.  JSCompiler's modifications to Rhino cause all
     * tokens located beyond the maximum column to MAX_COLUMN_NUMBER.
     */
    public static final int MAX_COLUMN_NUMBER = (1 << COLUMN_BITS) - 1;

    /**
     * COLUMN_MASK stores a value where bits storing the column number
     * are set, and bits storing the line are not set.  It's handy for
     * separating column number from line number.
     */
    public static final int COLUMN_MASK = MAX_COLUMN_NUMBER;

    /**
     * Source position of this node. The position is encoded with the
     * column number in the low 12 bits of the integer, and the line
     * number in the rest.  Create some handy constants so we can change this
     * size if we want.
     */
    private int sourcePosition;

    private JSType jsType;

    private Node parent;

    //==========================================================================
    /**
     * Merges the line number and character number in one integer. The Character
     * number takes the first 12 bits and the line number takes the rest. If
     * the character number is greater than <code>2<sup>12</sup>-1</code> it is
     * adjusted to <code>2<sup>12</sup>-1</code>.
     */
    protected static int mergeLineCharNo(int lineno, int charno) {
        if (lineno < 0 || charno < 0) {
            return -1;
        } else if ((charno & ~COLUMN_MASK) != 0) {
            return lineno << COLUMN_BITS | COLUMN_MASK;
        } else {
            return lineno << COLUMN_BITS | (charno & COLUMN_MASK);
        }
    }

    /**
     * Extracts the line number and character number from a merged line char
     * number (see {@link #mergeLineCharNo(int, int)}).
     */
    protected static int extractLineno(int lineCharNo) {
        if (lineCharNo == -1) {
            return -1;
        } else {
            return lineCharNo >>> COLUMN_BITS;
        }
    }

    //==========================================================================
    // Iteration
    //==========================================================================
    public int getChildCount() {
        int c = 0;
        for (Node n = first; n != null; n = n.next) c++;
        return c;
    }

    //==========================================================================
    // Mutators
    /**
     * @return A detached clone of the Node, specifically excluding its
     * children.
     */
    public Node cloneNode() {
        Node result;
        try {
            result = (Node) super.clone();
            result.next = null;
            result.first = null;
            result.last = null;
            result.parent = null;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e.getMessage());
        }
        return result;
    }

    /**
     * @return A detached clone of the Node and all its children.
     */
    public Node cloneTree() {
        Node result = cloneNode();
        for (Node n2 = getFirstChild(); n2 != null; n2 = n2.getNext()) {
            Node n2clone = n2.cloneTree();
            n2clone.parent = result;
            if (result.last != null) {
                result.last.next = n2clone;
            }
            if (result.first == null) {
                result.first = n2clone;
            }
            result.last = n2clone;
        }
        return result;
    }

    //==========================================================================
    // Custom annotations
    public JSType getJSType() {
        return jsType;
    }

    public void setJSType(JSType jsType) {
        this.jsType = jsType;
    }

    /**
     * Sets whether this node is a variable length argument node. This
     * method is meaningful only on {@link Token#NAME} nodes
     * used to define a {@link Token#FUNCTION}'s argument list.
     */
    public void setVarArgs(boolean varArgs) {
        putBooleanProp(VAR_ARGS_NAME, varArgs);
    }

    /**
     * Returns whether this node is a variable length argument node. This
     * method's return value is meaningful only on {@link Token#NAME} nodes
     * used to define a {@link Token#FUNCTION}'s argument list.
     */
    public boolean isVarArgs() {
        return getBooleanProp(VAR_ARGS_NAME);
    }

    /**
     * Sets whether this node is an optional argument node. This
     * method is meaningful only on {@link Token#NAME} nodes
     * used to define a {@link Token#FUNCTION}'s argument list.
     */
    public void setOptionalArg(boolean optionalArg) {
        putBooleanProp(OPT_ARG_NAME, optionalArg);
    }

    /**
     * Returns whether this node is an optional argument node. This
     * method's return value is meaningful only on {@link Token#NAME} nodes
     * used to define a {@link Token#FUNCTION}'s argument list.
     */
    public boolean isOptionalArg() {
        return getBooleanProp(OPT_ARG_NAME);
    }
}
