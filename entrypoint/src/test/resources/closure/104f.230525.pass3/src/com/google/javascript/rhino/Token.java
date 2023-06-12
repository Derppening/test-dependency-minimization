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
 *   Roger Lawrence
 *   Mike McCabe
 *   Igor Bukanov
 *   Milen Nankov
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

/**
 * This class implements the JavaScript scanner.
 *
 * It is based on the C source files jsscan.c and jsscan.h
 * in the jsref package.
 *
 * @see com.google.javascript.rhino.Parser
 */
public class Token {

    // debug flags
    public static final boolean printTrees = true;

    static final boolean printICode = false;

    static final boolean printNames = printTrees || printICode;

    /**
     * Token types.  These values correspond to JSTokenType values in
     * jsscan.c.
     */
    public final static int // start enum
    // well-known as the only code < EOF
    // end of file token - (not EOF_CHAR)
    // end of line
    ERROR = -1, // Interpreter reuses the following as bytecodes
    EOF = 0, // shallow equality (===)
    EOL = 1, // shallow equality (===)
    FIRST_BYTECODE_TOKEN = 2, // shallow equality (===)
    ENTERWITH = 2, // shallow equality (===)
    LEAVEWITH = 3, // shallow equality (===)
    RETURN = 4, // shallow equality (===)
    GOTO = 5, // shallow equality (===)
    IFEQ = 6, // shallow equality (===)
    IFNE = 7, // shallow equality (===)
    SETNAME = 8, // shallow equality (===)
    BITOR = 9, // shallow equality (===)
    BITXOR = 10, // shallow equality (===)
    BITAND = 11, // shallow equality (===)
    EQ = 12, // shallow equality (===)
    NE = 13, // shallow equality (===)
    LT = 14, // shallow equality (===)
    LE = 15, // shallow equality (===)
    GT = 16, // shallow equality (===)
    GE = 17, // shallow equality (===)
    LSH = 18, // shallow equality (===)
    RSH = 19, // shallow equality (===)
    URSH = 20, // shallow equality (===)
    ADD = 21, // shallow equality (===)
    SUB = 22, // shallow equality (===)
    MUL = 23, // shallow equality (===)
    DIV = 24, // shallow equality (===)
    MOD = 25, // shallow equality (===)
    NOT = 26, // shallow equality (===)
    BITNOT = 27, // shallow equality (===)
    POS = 28, // shallow equality (===)
    NEG = 29, // shallow equality (===)
    NEW = 30, // shallow equality (===)
    DELPROP = 31, // shallow equality (===)
    TYPEOF = 32, // shallow equality (===)
    GETPROP = 33, // shallow equality (===)
    SETPROP = 34, // shallow equality (===)
    GETELEM = 35, // shallow equality (===)
    SETELEM = 36, // shallow equality (===)
    CALL = 37, // shallow equality (===)
    NAME = 38, // shallow equality (===)
    NUMBER = 39, // shallow equality (===)
    STRING = 40, // shallow equality (===)
    NULL = 41, // shallow equality (===)
    THIS = 42, // shallow equality (===)
    FALSE = 43, // shallow inequality (!==)
    TRUE = 44, // rethrow caught execetion: catch (e if ) use it
    SHEQ = 45, // rethrow caught execetion: catch (e if ) use it
    SHNE = 46, // rethrow caught execetion: catch (e if ) use it
    REGEXP = 47, // rethrow caught execetion: catch (e if ) use it
    BINDNAME = 48, // to return prevoisly stored return result
    THROW = 49, // to return prevoisly stored return result
    RETHROW = 50, // to return prevoisly stored return result
    IN = 51, // to return prevoisly stored return result
    INSTANCEOF = 52, // to return prevoisly stored return result
    LOCAL_LOAD = 53, // to return prevoisly stored return result
    GETVAR = 54, // to return prevoisly stored return result
    SETVAR = 55, // to return prevoisly stored return result
    CATCH_SCOPE = 56, // to return prevoisly stored return result
    ENUM_INIT_KEYS = 57, // to return prevoisly stored return result
    ENUM_INIT_VALUES = 58, // to return prevoisly stored return result
    ENUM_NEXT = 59, // to return prevoisly stored return result
    ENUM_ID = 60, // array literal
    THISFN = 61, // object literal
    RETURN_RESULT = 62, // *reference
    ARRAYLIT = 63, // *reference    = something
    OBJECTLIT = 64, // delete reference
    GET_REF = 65, // f(args)    = something or f(args)++
    SET_REF = 66, // reference for special properties like __proto
    DEL_REF = 67, // For XML support:
    REF_CALL = 68, // default xml namespace =
    REF_SPECIAL = 69, // Reference for x.@y, x..y etc.
    // Reference for x.@y, x..y etc.
    DEFAULTNAMESPACE = 70, // Reference for x.@y, x..y etc.
    ESCXMLATTR = 71, // Reference for x.ns::y, x..ns::y etc.
    ESCXMLTEXT = 72, // Reference for @y, @[y] etc.
    REF_MEMBER = 73, // Reference for ns::y, @ns::y@[y] etc.
    REF_NS_MEMBER = 74, REF_NAME = 75, REF_NS_NAME = 76;

    // End of interpreter bytecodes
    public final static int // semicolon
    // semicolon
    LAST_BYTECODE_TOKEN = REF_NS_NAME, // left and right brackets
    TRY = 77, // left and right curlies (braces)
    SEMI = 78, // left and right curlies (braces)
    LB = 79, // left and right parentheses
    RB = 80, // left and right parentheses
    LC = 81, // comma operator
    RC = 82, // comma operator
    LP = 83, // simple assignment  (=)
    RP = 84, // |=
    COMMA = 85, // ^=
    ASSIGN = 86, // |=
    ASSIGN_BITOR = 87, // <<=
    ASSIGN_BITXOR = 88, // >>=
    ASSIGN_BITAND = 89, // >>>=
    ASSIGN_LSH = 90, // +=
    ASSIGN_RSH = 91, // -=
    ASSIGN_URSH = 92, // *=
    ASSIGN_ADD = 93, // /=
    ASSIGN_SUB = 94, // %=
    ASSIGN_MUL = 95, ASSIGN_DIV = 96, ASSIGN_MOD = 97;

    public final static int // conditional (?:)
    // conditional (?:)
    FIRST_ASSIGN = ASSIGN, // logical or (||)
    LAST_ASSIGN = ASSIGN_MOD, // logical or (||)
    HOOK = 98, // logical and (&&)
    COLON = 99, // increment/decrement (++ --)
    OR = 100, // member operator (.)
    AND = 101, // member operator (.)
    INC = 102, // function keyword
    DEC = 103, // export keyword
    DOT = 104, // import keyword
    FUNCTION = 105, // if keyword
    EXPORT = 106, // else keyword
    IMPORT = 107, // switch keyword
    IF = 108, // case keyword
    ELSE = 109, // default keyword
    SWITCH = 110, // while keyword
    CASE = 111, // do keyword
    DEFAULT = 112, // for keyword
    WHILE = 113, // break keyword
    DO = 114, // continue keyword
    FOR = 115, // var keyword
    BREAK = 116, // with keyword
    CONTINUE = 117, // catch keyword
    VAR = 118, // finally keyword
    WITH = 119, // void keyword
    CATCH = 120, // reserved keywords
    FINALLY = 121, VOID = 122, RESERVED = 123, EMPTY = 124, /* types used for the parse tree - these never get returned
         * by the scanner.
         */
    // statement block
    // label
    // expression statement in functions
    BLOCK = 125, // expression statement in functions
    LABEL = 126, // expression statement in functions
    TARGET = 127, // expression statement in scripts
    LOOP = 128, // top-level node for entire script
    EXPR_VOID = 129, // top-level node for entire script
    EXPR_RESULT = 130, // for typeof(simple-name)
    JSR = 131, // x.y op= something
    SCRIPT = 132, // x.y op= something
    TYPEOFNAME = 133, // x[y] op= something
    USE_STACK = 134, // *reference op= something
    SETPROP_OP = 135, // *reference op= something
    SETELEM_OP = 136, // For XML support:
    LOCAL_BLOCK = 137, // member operator (..)
    SET_REF_OP = 138, // namespace::name
    // XML type
    DOTDOT = 139, // .() -- e.g., x.emps.emp.(name == "terry")
    COLONCOLON = 140, // @
    XML = 141, // Optimizer-only-tokens
    DOTQUERY = 142, // Optimizer-only-tokens
    XMLATTR = 143, // JS 1.5 get pseudo keyword
    XMLEND = 144, // JS 1.5 get pseudo keyword
    TO_OBJECT = 145, // JS 1.5 set pseudo keyword
    TO_DOUBLE = 146, // JS 1.5 const keyword
    GET = 147, // JSDoc-only tokens
    SET = 148, // JSDoc-only tokens
    CONST = 149, // JSDoc-only tokens
    SETCONST = 150, // JSDoc-only tokens
    SETCONSTVAR = 151, // JSDoc-only tokens
    DEBUGGER = 152, LAST_TOKEN = 152, ANNOTATION = 300, PIPE = 301, STAR = 302, EOC = 303, QMARK = 304, ELLIPSIS = 305, BANG = 306, EQUALS = 307;

    public static String name(int token) {
        if (!printNames) {
            return String.valueOf(token);
        }
        switch(token) {
            case ERROR:
                return "ERROR";
            case EOF:
                return "EOF";
            case EOL:
                return "EOL";
            case ENTERWITH:
                return "ENTERWITH";
            case LEAVEWITH:
                return "LEAVEWITH";
            case RETURN:
                return "RETURN";
            case GOTO:
                return "GOTO";
            case IFEQ:
                return "IFEQ";
            case IFNE:
                return "IFNE";
            case SETNAME:
                return "SETNAME";
            case BITOR:
                return "BITOR";
            case BITXOR:
                return "BITXOR";
            case BITAND:
                return "BITAND";
            case EQ:
                return "EQ";
            case NE:
                return "NE";
            case LT:
                return "LT";
            case LE:
                return "LE";
            case GT:
                return "GT";
            case GE:
                return "GE";
            case LSH:
                return "LSH";
            case RSH:
                return "RSH";
            case URSH:
                return "URSH";
            case ADD:
                return "ADD";
            case SUB:
                return "SUB";
            case MUL:
                return "MUL";
            case DIV:
                return "DIV";
            case MOD:
                return "MOD";
            case NOT:
                return "NOT";
            case BITNOT:
                return "BITNOT";
            case POS:
                return "POS";
            case NEG:
                return "NEG";
            case NEW:
                return "NEW";
            case DELPROP:
                return "DELPROP";
            case TYPEOF:
                return "TYPEOF";
            case GETPROP:
                return "GETPROP";
            case SETPROP:
                return "SETPROP";
            case GETELEM:
                return "GETELEM";
            case SETELEM:
                return "SETELEM";
            case CALL:
                return "CALL";
            case NAME:
                return "NAME";
            case NUMBER:
                return "NUMBER";
            case STRING:
                return "STRING";
            case NULL:
                return "NULL";
            case THIS:
                return "THIS";
            case FALSE:
                return "FALSE";
            case TRUE:
                return "TRUE";
            case SHEQ:
                return "SHEQ";
            case SHNE:
                return "SHNE";
            case REGEXP:
                return "OBJECT";
            case BINDNAME:
                return "BINDNAME";
            case THROW:
                return "THROW";
            case RETHROW:
                return "RETHROW";
            case IN:
                return "IN";
            case INSTANCEOF:
                return "INSTANCEOF";
            case LOCAL_LOAD:
                return "LOCAL_LOAD";
            case GETVAR:
                return "GETVAR";
            case SETVAR:
                return "SETVAR";
            case CATCH_SCOPE:
                return "CATCH_SCOPE";
            case ENUM_INIT_KEYS:
                return "ENUM_INIT_KEYS";
            case ENUM_INIT_VALUES:
                return "ENUM_INIT_VALUES";
            case ENUM_NEXT:
                return "ENUM_NEXT";
            case ENUM_ID:
                return "ENUM_ID";
            case THISFN:
                return "THISFN";
            case RETURN_RESULT:
                return "RETURN_RESULT";
            case ARRAYLIT:
                return "ARRAYLIT";
            case OBJECTLIT:
                return "OBJECTLIT";
            case GET_REF:
                return "GET_REF";
            case SET_REF:
                return "SET_REF";
            case DEL_REF:
                return "DEL_REF";
            case REF_CALL:
                return "REF_CALL";
            case REF_SPECIAL:
                return "REF_SPECIAL";
            case DEFAULTNAMESPACE:
                return "DEFAULTNAMESPACE";
            case ESCXMLTEXT:
                return "ESCXMLTEXT";
            case ESCXMLATTR:
                return "ESCXMLATTR";
            case REF_MEMBER:
                return "REF_MEMBER";
            case REF_NS_MEMBER:
                return "REF_NS_MEMBER";
            case REF_NAME:
                return "REF_NAME";
            case REF_NS_NAME:
                return "REF_NS_NAME";
            case TRY:
                return "TRY";
            case SEMI:
                return "SEMI";
            case LB:
                return "LB";
            case RB:
                return "RB";
            case LC:
                return "LC";
            case RC:
                return "RC";
            case LP:
                return "LP";
            case RP:
                return "RP";
            case COMMA:
                return "COMMA";
            case ASSIGN:
                return "ASSIGN";
            case ASSIGN_BITOR:
                return "ASSIGN_BITOR";
            case ASSIGN_BITXOR:
                return "ASSIGN_BITXOR";
            case ASSIGN_BITAND:
                return "ASSIGN_BITAND";
            case ASSIGN_LSH:
                return "ASSIGN_LSH";
            case ASSIGN_RSH:
                return "ASSIGN_RSH";
            case ASSIGN_URSH:
                return "ASSIGN_URSH";
            case ASSIGN_ADD:
                return "ASSIGN_ADD";
            case ASSIGN_SUB:
                return "ASSIGN_SUB";
            case ASSIGN_MUL:
                return "ASSIGN_MUL";
            case ASSIGN_DIV:
                return "ASSIGN_DIV";
            case ASSIGN_MOD:
                return "ASSIGN_MOD";
            case HOOK:
                return "HOOK";
            case COLON:
                return "COLON";
            case OR:
                return "OR";
            case AND:
                return "AND";
            case INC:
                return "INC";
            case DEC:
                return "DEC";
            case DOT:
                return "DOT";
            case FUNCTION:
                return "FUNCTION";
            case EXPORT:
                return "EXPORT";
            case IMPORT:
                return "IMPORT";
            case IF:
                return "IF";
            case ELSE:
                return "ELSE";
            case SWITCH:
                return "SWITCH";
            case CASE:
                return "CASE";
            case DEFAULT:
                return "DEFAULT";
            case WHILE:
                return "WHILE";
            case DO:
                return "DO";
            case FOR:
                return "FOR";
            case BREAK:
                return "BREAK";
            case CONTINUE:
                return "CONTINUE";
            case VAR:
                return "VAR";
            case WITH:
                return "WITH";
            case CATCH:
                return "CATCH";
            case FINALLY:
                return "FINALLY";
            case RESERVED:
                return "RESERVED";
            case EMPTY:
                return "EMPTY";
            case BLOCK:
                return "BLOCK";
            case LABEL:
                return "LABEL";
            case TARGET:
                return "TARGET";
            case LOOP:
                return "LOOP";
            case EXPR_VOID:
                return "EXPR_VOID";
            case EXPR_RESULT:
                return "EXPR_RESULT";
            case JSR:
                return "JSR";
            case SCRIPT:
                return "SCRIPT";
            case TYPEOFNAME:
                return "TYPEOFNAME";
            case USE_STACK:
                return "USE_STACK";
            case SETPROP_OP:
                return "SETPROP_OP";
            case SETELEM_OP:
                return "SETELEM_OP";
            case LOCAL_BLOCK:
                return "LOCAL_BLOCK";
            case SET_REF_OP:
                return "SET_REF_OP";
            case DOTDOT:
                return "DOTDOT";
            case COLONCOLON:
                return "COLONCOLON";
            case XML:
                return "XML";
            case DOTQUERY:
                return "DOTQUERY";
            case XMLATTR:
                return "XMLATTR";
            case XMLEND:
                return "XMLEND";
            case TO_OBJECT:
                return "TO_OBJECT";
            case TO_DOUBLE:
                return "TO_DOUBLE";
            case GET:
                return "GET";
            case SET:
                return "SET";
            case CONST:
                return "CONST";
            case SETCONST:
                return "SETCONST";
            case DEBUGGER:
                return "DEBUGGER";
            case ANNOTATION:
                return "ANNOTATION";
            case PIPE:
                return "PIPE";
            case STAR:
                return "STAR";
            case EOC:
                return "EOC";
            case QMARK:
                return "QMARK";
            case ELLIPSIS:
                return "ELLIPSIS";
            case BANG:
                return "BANG";
            case VOID:
                return "VOID";
            case EQUALS:
                return "EQUALS";
        }
        // Token without name
        throw new IllegalStateException(String.valueOf(token));
    }
}
