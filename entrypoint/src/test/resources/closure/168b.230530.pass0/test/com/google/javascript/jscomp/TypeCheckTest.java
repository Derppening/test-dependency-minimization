/*
 * Copyright 2006 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Arrays;

/**
 * Tests {@link TypeCheck}.
 */
public class TypeCheckTest extends CompilerTypeTestCase {

    private CheckLevel reportMissingOverrides = CheckLevel.WARNING;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        reportMissingOverrides = CheckLevel.WARNING;
    }

    // TODO(nicksantos): change this to something that makes sense.
    //   public void testComparison1() throws Exception {
    //     testTypes("/**@type null */var a;" +
    //         "/**@type !Date */var b;" +
    //         "if (a==b) {}",
    //         "condition always evaluates to false\n" +
    //         "left : null\n" +
    //         "right: Date");
    //   }
    public void testIssue726() throws Exception {
        testTypes("/** @constructor */ function Foo() {}" + "/** @param {number} x */ Foo.prototype.bar = function(x) {};" + "/** @return {!Function} */ " + "Foo.prototype.getDeferredBar = function() { " + "  var self = this;" + "  return function() {" + "    self.bar(true);" + "  };" + "};", "actual parameter 1 of Foo.prototype.bar does not match formal parameter\n" + "found   : boolean\n" + "required: number");
    }

    // According to ECMA-262, Error & Array function calls are equivalent to
    // constructor calls.
    // TODO(user): We should flag these as invalid. This will probably happen
    // when we make sure the interface is never referenced outside of its
    // definition. We might want more specific and helpful error messages.
    //public void testWarningOnInterfacePrototype() throws Exception {
    //  testTypes("/** @interface */ u.T = function() {};\n" +
    //      "/** @return {number} */ u.T.prototype = function() { };",
    //      "e of its definition");
    //}
    //
    //public void testBadPropertyOnInterface1() throws Exception {
    //  testTypes("/** @interface */ u.T = function() {};\n" +
    //      "/** @return {number} */ u.T.f = function() { return 1;};",
    //      "cannot reference an interface outside of its definition");
    //}
    //
    //public void testBadPropertyOnInterface2() throws Exception {
    //  testTypes("/** @interface */ function T() {};\n" +
    //      "/** @return {number} */ T.f = function() { return 1;};",
    //      "cannot reference an interface outside of its definition");
    //}
    //
    //public void testBadPropertyOnInterface3() throws Exception {
    //  testTypes("/** @interface */ u.T = function() {}; u.T.x",
    //      "cannot reference an interface outside of its definition");
    //}
    //
    //public void testBadPropertyOnInterface4() throws Exception {
    //  testTypes("/** @interface */ function T() {}; T.x;",
    //      "cannot reference an interface outside of its definition");
    //}
    // TODO(user): If we want to support this syntax we have to warn about
    // missing annotations.
    //public void testWarnUnannotatedPropertyOnInterface1() throws Exception {
    //  testTypes("/** @interface */ u.T = function () {}; u.T.prototype.x;",
    //      "interface property x is not annotated");
    //}
    //
    //public void testWarnUnannotatedPropertyOnInterface2() throws Exception {
    //  testTypes("/** @interface */ function T() {}; T.prototype.x;",
    //      "interface property x is not annotated");
    //}
    // TODO(user): If we want to support this syntax we have to warn about
    // the invalid type of the interface member.
    //public void testWarnDataPropertyOnInterface1() throws Exception {
    //  testTypes("/** @interface */ u.T = function () {};\n" +
    //      "/** @type {number} */u.T.prototype.x;",
    //      "interface members can only be plain functions");
    //}
    // TODO(user): If we want to support this syntax we should warn about the
    // mismatching types in the two tests below.
    //public void testErrorMismatchingPropertyOnInterface1() throws Exception {
    //  testTypes("/** @interface */ u.T = function () {};\n" +
    //      "/** @param {Number} foo */u.T.prototype.x =\n" +
    //      "/** @param {String} foo */function(foo) {};",
    //      "found   : \n" +
    //      "required: ");
    //}
    //
    //public void testErrorMismatchingPropertyOnInterface2() throws Exception {
    //  testTypes("/** @interface */ function T() {};\n" +
    //      "/** @return {number} */T.prototype.x =\n" +
    //      "/** @return {string} */function() {};",
    //      "found   : \n" +
    //      "required: ");
    //}
    // TODO(user): We should warn about this (bar is missing an annotation). We
    // probably don't want to warn about all missing parameter annotations, but
    // we should be as strict as possible regarding interfaces.
    //public void testErrorMismatchingPropertyOnInterface3() throws Exception {
    //  testTypes("/** @interface */ u.T = function () {};\n" +
    //      "/** @param {Number} foo */u.T.prototype.x =\n" +
    //      "function(foo, bar) {};",
    //      "found   : \n" +
    //      "required: ");
    //}
    // In all testResolutionViaRegistry* tests, since u is unknown, u.T can only
    // be resolved via the registry and not via properties.
    private void testTypes(String js, String description) throws Exception {
        testTypes(js, description, false);
    }

    void testTypes(String js, String description, boolean isError) throws Exception {
        testTypes(DEFAULT_EXTERNS, js, description, isError);
    }

    void testTypes(String externs, String js, String description, boolean isError) throws Exception {
        Node n = parseAndTypeCheck(externs, js);
        JSError[] errors = compiler.getErrors();
        if (description != null && isError) {
            assertTrue("expected an error", errors.length > 0);
            assertEquals(description, errors[0].description);
            errors = Arrays.asList(errors).subList(1, errors.length).toArray(new JSError[errors.length - 1]);
        }
        if (errors.length > 0) {
            fail("unexpected error(s):\n" + Joiner.on("\n").join(errors));
        }
        JSError[] warnings = compiler.getWarnings();
        if (description != null && !isError) {
            assertTrue("expected a warning", warnings.length > 0);
            assertEquals(description, warnings[0].description);
            warnings = Arrays.asList(warnings).subList(1, warnings.length).toArray(new JSError[warnings.length - 1]);
        }
        if (warnings.length > 0) {
            fail("unexpected warnings(s):\n" + Joiner.on("\n").join(warnings));
        }
    }

    private Node parseAndTypeCheck(String externs, String js) {
        return parseAndTypeCheckWithScope(externs, js).root;
    }

    private TypeCheckResult parseAndTypeCheckWithScope(String externs, String js) {
        compiler.init(Lists.newArrayList(SourceFile.fromCode("[externs]", externs)), Lists.newArrayList(SourceFile.fromCode("[testcode]", js)), compiler.getOptions());
        Node n = compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler);
        Node externsNode = compiler.getInput(new InputId("[externs]")).getAstRoot(compiler);
        Node externAndJsRoot = new Node(Token.BLOCK, externsNode, n);
        externAndJsRoot.setIsSyntheticBlock(true);
        assertEquals("parsing error: " + Joiner.on(", ").join(compiler.getErrors()), 0, compiler.getErrorCount());
        Scope s = makeTypeCheck().processForTesting(externsNode, n);
        return new TypeCheckResult(n, s);
    }

    private TypeCheck makeTypeCheck() {
        return new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(compiler.getCodingConvention(), registry), registry, reportMissingOverrides, CheckLevel.OFF);
    }

    private static class TypeCheckResult {

        private final Node root;

        private final Scope scope;

        private TypeCheckResult(Node root, Scope scope) {
            this.root = root;
            this.scope = scope;
        }
    }
}
