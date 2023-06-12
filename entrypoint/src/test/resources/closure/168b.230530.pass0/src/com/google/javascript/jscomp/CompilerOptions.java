/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.SourcePosition;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiler options
 * @author nicksantos@google.com (Nick Santos)
 */
public class CompilerOptions implements Serializable, Cloneable {

    // Unused. For people using reflection to circumvent access control.
    @SuppressWarnings("unused")
    private boolean manageClosureDependencies = false;

    // TODO(nicksantos): All public properties of this class should be made
    // package-private, and have a public setter.
    private static final long serialVersionUID = 7L;

    /**
     * The JavaScript language version accepted.
     */
    private LanguageMode languageIn;

    /**
     * The JavaScript language version that should be produced.
     * Currently, this is always the same as {@link #languageIn}.
     */
    private LanguageMode languageOut;

    /**
     * Whether the compiler accepts the `const' keyword.
     */
    boolean acceptConstKeyword;

    /**
     * Whether the compiler should assume that a function's "this" value
     * never needs coercion (for example in non-strict "null" or "undefined" will
     * be coerced to the global "this" and primitives to objects).
     */
    private boolean assumeStrictThis;

    /**
     * Configures the compiler for use as an IDE backend.  In this mode:
     * <ul>
     *  <li>No optimization passes will run.</li>
     *  <li>The last time custom passes are invoked is
     *      {@link CustomPassExecutionTime#BEFORE_OPTIMIZATIONS}</li>
     *  <li>The compiler will always try to process all inputs fully, even
     *      if it encounters errors.</li>
     *  <li>The compiler may record more information than is strictly
     *      needed for codegen.</li>
     * </ul>
     */
    public boolean ideMode;

    boolean saveDataStructures = false;

    /**
     * Configures the compiler to skip as many passes as possible.
     */
    boolean skipAllPasses;

    /**
     * If true, name anonymous functions only. All others passes will be skipped.
     */
    boolean nameAnonymousFunctionsOnly;

    /**
     * Configures the compiler to run expensive sanity checks after
     * every pass. Only intended for internal development.
     */
    DevMode devMode;

    //--------------------------------
    // Input Options
    //--------------------------------
    DependencyOptions dependencyOptions = new DependencyOptions();

    /**
     * Returns localized replacement for MSG_* variables
     */
    // Transient so that clients don't have to implement Serializable.
    public transient MessageBundle messageBundle = null;

    //--------------------------------
    // Checks
    //--------------------------------
    /**
     * Checks that all symbols are defined
     */
    public boolean checkSymbols;

    public CheckLevel aggressiveVarCheck;

    /**
     * Checks for suspicious statements that have no effect
     */
    public boolean checkSuspiciousCode;

    /**
     * Checks for invalid control structures
     */
    public boolean checkControlStructures;

    /**
     * Checks types on expressions
     */
    public boolean checkTypes;

    boolean tightenTypes;

    public CheckLevel reportMissingOverride;

    CheckLevel reportUnknownTypes;

    /**
     * Checks for missing goog.require() calls *
     */
    public CheckLevel checkRequires;

    public CheckLevel checkProvides;

    public CheckLevel checkGlobalNamesLevel;

    public CheckLevel brokenClosureRequiresLevel;

    public CheckLevel checkGlobalThisLevel;

    public CheckLevel checkMissingGetCssNameLevel;

    /**
     * Regex of string literals that may only appear in goog.getCssName arguments.
     */
    public String checkMissingGetCssNameBlacklist;

    /**
     * Checks that the syntactic restrictions of Caja are met.
     */
    boolean checkCaja;

    /**
     * A set of extra annotation names which are accepted and silently ignored
     * when encountered in a source file. Defaults to null which has the same
     * effect as specifying an empty set.
     */
    Set<String> extraAnnotationNames;

    //--------------------------------
    // Optimizations
    //--------------------------------
    /**
     * Folds constants (e.g. (2 + 3) to 5)
     */
    public boolean foldConstants;

    /**
     * Remove assignments to values that can not be referenced
     */
    public boolean deadAssignmentElimination;

    /**
     * Inlines constants (symbols that are all CAPS)
     */
    public boolean inlineConstantVars;

    /**
     * Inlines global functions
     */
    public boolean inlineFunctions;

    /**
     * Inlines functions defined in local scopes
     */
    public boolean inlineLocalFunctions;

    /**
     * More aggressive function inlining
     */
    boolean assumeClosuresOnlyCaptureReferences;

    /**
     * Inlines properties
     */
    boolean inlineProperties;

    /**
     * Move code to a deeper module
     */
    public boolean crossModuleCodeMotion;

    /**
     * Merge two variables together as one.
     */
    public boolean coalesceVariableNames;

    /**
     * Move methods to a deeper module
     */
    public boolean crossModuleMethodMotion;

    /**
     * Inlines trivial getters
     */
    public boolean inlineGetters;

    /**
     * Inlines variables
     */
    public boolean inlineVariables;

    /**
     * Inlines variables
     */
    boolean inlineLocalVariables;

    // TODO(user): This is temporary. Once flow sensitive inlining is stable
    /**
     * Removes code associated with unused global names
     */
    public boolean smartNameRemoval;

    /**
     * Removes code that will never execute
     */
    public boolean removeDeadCode;

    public CheckLevel checkUnreachableCode;

    public CheckLevel checkMissingReturn;

    /**
     * Extracts common prototype member declarations
     */
    public boolean extractPrototypeMemberDeclarations;

    /**
     * Removes unused member prototypes
     */
    public boolean removeUnusedPrototypeProperties;

    /**
     * Tells AnalyzePrototypeProperties it can remove externed props.
     */
    public boolean removeUnusedPrototypePropertiesInExterns;

    /**
     * Removes unused member properties
     */
    public boolean removeUnusedClassProperties;

    /**
     * Removes unused variables
     */
    public boolean removeUnusedVars;

    /**
     * Removes unused variables in local scope.
     */
    public boolean removeUnusedLocalVars;

    /**
     * Adds variable aliases for externals to reduce code size
     */
    public boolean aliasExternals;

    /**
     * Collapses multiple variable declarations into one
     */
    public boolean collapseVariableDeclarations;

    /**
     * Group multiple variable declarations into one
     */
    boolean groupVariableDeclarations;

    /**
     * Collapses anonymous function declarations into named function
     * declarations
     */
    public boolean collapseAnonymousFunctions;

    /**
     * If set to a non-empty set, those strings literals will be aliased to a
     * single global instance per string, to avoid creating more objects than
     * necessary.
     */
    public Set<String> aliasableStrings;

    /**
     * A blacklist in the form of a regular expression to block strings that
     * contains certain words from being aliased.
     * If the value is the empty string, no words are blacklisted.
     */
    public String aliasStringsBlacklist;

    /**
     * Aliases all string literals to global instances, to avoid creating more
     * objects than necessary (if true, overrides any set of strings passed in
     * to aliasableStrings)
     */
    public boolean aliasAllStrings;

    /**
     * Print string usage as part of the compilation log.
     */
    boolean outputJsStringUsage;

    /**
     * Converts quoted property accesses to dot syntax (a['b'] -> a.b)
     */
    public boolean convertToDottedProperties;

    /**
     * Reduces the size of common function expressions.
     */
    public boolean rewriteFunctionExpressions;

    /**
     * Remove unused and constant parameters.
     */
    public boolean optimizeParameters;

    /**
     * Remove unused return values.
     */
    public boolean optimizeReturns;

    /**
     * Chains calls to functions that return this.
     */
    boolean chainCalls;

    //--------------------------------
    // Renaming
    //--------------------------------
    /**
     * Controls which variables get renamed.
     */
    public VariableRenamingPolicy variableRenaming;

    /**
     * Controls which properties get renamed.
     */
    public PropertyRenamingPolicy propertyRenaming;

    /**
     * Should we use affinity information when generating property names.
     */
    boolean propertyAffinity;

    /**
     * Controls label renaming.
     */
    public boolean labelRenaming;

    /**
     * Should shadow variable names in outer scope.
     */
    boolean shadowVariables;

    /**
     * Generate pseudo names for variables and properties for debugging purposes.
     */
    public boolean generatePseudoNames;

    /**
     * Specifies a prefix for all globals
     */
    public String renamePrefix;

    /**
     * Aliases true, false, and null to variables with shorter names.
     */
    public boolean aliasKeywords;

    /**
     * Flattens multi-level property names (e.g. a$b = x)
     */
    public boolean collapseProperties;

    /**
     * Split object literals into individual variables when possible.
     */
    boolean collapseObjectLiterals;

    /**
     * Flattens multi-level property names on extern types (e.g. String$f = x)
     */
    boolean collapsePropertiesOnExternTypes;

    /**
     * Devirtualize prototype method by rewriting them to be static calls that
     * take the this pointer as their first argument
     */
    public boolean devirtualizePrototypeMethods;

    /**
     * Use @nosideeffects annotations, function bodies and name graph
     * to determine if calls have side effects.  Requires --check_types.
     */
    public boolean computeFunctionSideEffects;

    /**
     * Where to save debug report for compute function side effects.
     */
    String debugFunctionSideEffectsPath;

    /**
     * Rename properties to disambiguate between unrelated fields based on
     * type information.
     */
    public boolean disambiguateProperties;

    /**
     * Rename unrelated properties to the same name to reduce code size.
     */
    public boolean ambiguateProperties;

    /**
     * Give anonymous functions names for easier debugging
     */
    public AnonymousFunctionNamingPolicy anonymousFunctionNaming;

    /**
     * Whether to export test functions.
     */
    public boolean exportTestFunctions;

    //--------------------------------
    // Special-purpose alterations
    //--------------------------------
    /**
     * Inserts run-time type assertions for debugging.
     */
    boolean runtimeTypeCheck;

    /**
     * A JS function to be used for logging run-time type assertion
     * failures. It will be passed the warning as a string and the
     * faulty expression as arguments.
     */
    String runtimeTypeCheckLogFunction;

    /**
     * A CodingConvention to use during the compile.
     */
    private CodingConvention codingConvention;

    boolean ignoreCajaProperties;

    public String syntheticBlockStartMarker;

    public String syntheticBlockEndMarker;

    /**
     * Compiling locale
     */
    public String locale;

    /**
     * Sets the special "COMPILED" value to true
     */
    public boolean markAsCompiled;

    /**
     * Removes try...catch...finally blocks for easier debugging
     */
    public boolean removeTryCatchFinally;

    /**
     * Processes goog.provide() and goog.require() calls
     */
    public boolean closurePass;

    /**
     * Processes jQuery aliases
     */
    public boolean jqueryPass;

    /**
     * Remove goog.abstractMethod assignments.
     */
    boolean removeAbstractMethods;

    /**
     * Remove goog.asserts calls.
     */
    boolean removeClosureAsserts;

    /**
     * Names of types to strip
     */
    public Set<String> stripTypes;

    /**
     * Name suffixes that determine which variables and properties to strip
     */
    public Set<String> stripNameSuffixes;

    /**
     * Name prefixes that determine which variables and properties to strip
     */
    public Set<String> stripNamePrefixes;

    /**
     * Qualified type name prefixes that determine which types to strip
     */
    public Set<String> stripTypePrefixes;

    /**
     * Custom passes
     */
    public transient Multimap<CustomPassExecutionTime, CompilerPass> customPasses;

    /**
     * Mark no side effect calls
     */
    public boolean markNoSideEffectCalls;

    /**
     * Replacements for @defines. Will be Boolean, Numbers, or Strings
     */
    private Map<String, Object> defineReplacements;

    /**
     * What kind of processing to do for goog.tweak functions.
     */
    private TweakProcessing tweakProcessing;

    /**
     * Replacements for tweaks. Will be Boolean, Numbers, or Strings
     */
    private Map<String, Object> tweakReplacements;

    /**
     * Move top-level function declarations to the top
     */
    public boolean moveFunctionDeclarations;

    /**
     * Instrumentation template to use with #recordFunctionInformation
     */
    public String instrumentationTemplate;

    String appNameStr;

    /**
     * Record function information
     */
    public boolean recordFunctionInformation;

    public boolean generateExports;

    /**
     * Map used in the renaming of CSS class names.
     */
    public CssRenamingMap cssRenamingMap;

    /**
     * Process instances of goog.testing.ObjectPropertyString.
     */
    boolean processObjectPropertyString;

    /**
     * Replace id generators
     */
    // true by default for legacy reasons.
    boolean replaceIdGenerators = true;

    /**
     * Id generators to replace.
     */
    Set<String> idGenerators;

    /**
     * Configuration strings
     */
    List<String> replaceStringsFunctionDescriptions;

    String replaceStringsPlaceholderToken;

    // A list of strings that should not be used as replacements
    Set<String> replaceStringsReservedStrings;

    /**
     * List of properties that we report invalidation errors for.
     */
    Map<String, CheckLevel> propertyInvalidationErrors;

    /**
     * Transform AMD to CommonJS modules.
     */
    boolean transformAMDToCJSModules = false;

    /**
     * Rewrite CommonJS modules so that they can be concatenated together.
     */
    boolean processCommonJSModules = false;

    /**
     * CommonJS module prefix.
     */
    String commonJSModulePathPrefix = ProcessCommonJSModules.DEFAULT_FILENAME_PREFIX;

    //--------------------------------
    // Output options
    //--------------------------------
    /**
     * Output in pretty indented format
     */
    public boolean prettyPrint;

    /**
     * Line break the output a bit more aggressively
     */
    public boolean lineBreak;

    /**
     * Prefer line breaks at end of file
     */
    public boolean preferLineBreakAtEndOfFile;

    /**
     * Prints a separator comment before each JS script
     */
    public boolean printInputDelimiter;

    /**
     * The string to use as the separator for printInputDelimiter
     */
    public String inputDelimiter = "// Input %num%";

    String reportPath;

    TracerMode tracer;

    private boolean colorizeErrorOutput;

    public ErrorFormat errorFormat;

    private ComposeWarningsGuard warningsGuard = new ComposeWarningsGuard();

    int summaryDetailLevel = 1;

    int lineLengthThreshold = CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD;

    //--------------------------------
    // Special Output Options
    //--------------------------------
    /**
     * Whether the exports should be made available via {@link Result} after
     * compilation. This is implicitly true if {@link #externExportsPath} is set.
     */
    private boolean externExports;

    String nameReferenceReportPath;

    String nameReferenceGraphPath;

    //--------------------------------
    // Debugging Options
    //--------------------------------
    /**
     * The output path for the source map.
     */
    public String sourceMapOutputPath;

    /**
     * The detail level for the generated source map.
     */
    public SourceMap.DetailLevel sourceMapDetailLevel = SourceMap.DetailLevel.SYMBOLS;

    /**
     * The source map file format
     */
    public SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;

    public List<SourceMap.LocationMapping> sourceMapLocationMappings = Collections.emptyList();

    /**
     * Charset to use when generating code.  If null, then output ASCII.
     * This needs to be a string because CompilerOptions is serializable.
     */
    String outputCharset;

    /**
     * Whether the named objects types included 'undefined' by default.
     */
    boolean looseTypes;

    /**
     * Data holder Alias Transformation information accumulated during a compile.
     */
    private transient AliasTransformationHandler aliasHandler;

    /**
     * Handler for compiler warnings and errors.
     */
    transient ErrorHandler errorHandler;

    /**
     * Initializes compiler options. All options are disabled by default.
     *
     * Command-line frontends to the compiler should set these properties
     * like a builder.
     */
    public CompilerOptions() {
        // Accepted language
        languageIn = LanguageMode.ECMASCRIPT3;
        // Language variation
        acceptConstKeyword = false;
        // Checks
        skipAllPasses = false;
        nameAnonymousFunctionsOnly = false;
        devMode = DevMode.OFF;
        checkSymbols = false;
        aggressiveVarCheck = CheckLevel.OFF;
        checkSuspiciousCode = false;
        checkControlStructures = false;
        checkTypes = false;
        tightenTypes = false;
        reportMissingOverride = CheckLevel.OFF;
        reportUnknownTypes = CheckLevel.OFF;
        checkRequires = CheckLevel.OFF;
        checkProvides = CheckLevel.OFF;
        checkGlobalNamesLevel = CheckLevel.OFF;
        brokenClosureRequiresLevel = CheckLevel.ERROR;
        checkGlobalThisLevel = CheckLevel.OFF;
        checkUnreachableCode = CheckLevel.OFF;
        checkMissingReturn = CheckLevel.OFF;
        checkMissingGetCssNameLevel = CheckLevel.OFF;
        checkMissingGetCssNameBlacklist = null;
        checkCaja = false;
        computeFunctionSideEffects = false;
        chainCalls = false;
        extraAnnotationNames = null;
        // Optimizations
        foldConstants = false;
        coalesceVariableNames = false;
        deadAssignmentElimination = false;
        inlineConstantVars = false;
        inlineFunctions = false;
        inlineLocalFunctions = false;
        assumeStrictThis = false;
        assumeClosuresOnlyCaptureReferences = false;
        inlineProperties = false;
        crossModuleCodeMotion = false;
        crossModuleMethodMotion = false;
        inlineGetters = false;
        inlineVariables = false;
        inlineLocalVariables = false;
        smartNameRemoval = false;
        removeDeadCode = false;
        extractPrototypeMemberDeclarations = false;
        removeUnusedPrototypeProperties = false;
        removeUnusedPrototypePropertiesInExterns = false;
        removeUnusedClassProperties = false;
        removeUnusedVars = false;
        removeUnusedLocalVars = false;
        aliasExternals = false;
        collapseVariableDeclarations = false;
        groupVariableDeclarations = false;
        collapseAnonymousFunctions = false;
        aliasableStrings = Collections.emptySet();
        aliasStringsBlacklist = "";
        aliasAllStrings = false;
        outputJsStringUsage = false;
        convertToDottedProperties = false;
        rewriteFunctionExpressions = false;
        optimizeParameters = false;
        optimizeReturns = false;
        // Renaming
        variableRenaming = VariableRenamingPolicy.OFF;
        propertyRenaming = PropertyRenamingPolicy.OFF;
        propertyAffinity = false;
        labelRenaming = false;
        generatePseudoNames = false;
        shadowVariables = false;
        renamePrefix = null;
        aliasKeywords = false;
        collapseProperties = false;
        collapsePropertiesOnExternTypes = false;
        collapseObjectLiterals = false;
        devirtualizePrototypeMethods = false;
        disambiguateProperties = false;
        ambiguateProperties = false;
        anonymousFunctionNaming = AnonymousFunctionNamingPolicy.OFF;
        exportTestFunctions = false;
        // Alterations
        runtimeTypeCheck = false;
        runtimeTypeCheckLogFunction = null;
        ignoreCajaProperties = false;
        syntheticBlockStartMarker = null;
        syntheticBlockEndMarker = null;
        locale = null;
        markAsCompiled = false;
        removeTryCatchFinally = false;
        closurePass = false;
        jqueryPass = false;
        removeAbstractMethods = true;
        removeClosureAsserts = false;
        stripTypes = Collections.emptySet();
        stripNameSuffixes = Collections.emptySet();
        stripNamePrefixes = Collections.emptySet();
        stripTypePrefixes = Collections.emptySet();
        customPasses = null;
        markNoSideEffectCalls = false;
        defineReplacements = Maps.newHashMap();
        tweakProcessing = TweakProcessing.OFF;
        tweakReplacements = Maps.newHashMap();
        moveFunctionDeclarations = false;
        instrumentationTemplate = null;
        appNameStr = "";
        recordFunctionInformation = false;
        generateExports = false;
        cssRenamingMap = null;
        processObjectPropertyString = false;
        idGenerators = Collections.emptySet();
        replaceStringsFunctionDescriptions = Collections.emptyList();
        replaceStringsPlaceholderToken = "";
        replaceStringsReservedStrings = Collections.emptySet();
        propertyInvalidationErrors = Maps.newHashMap();
        // Output
        printInputDelimiter = false;
        prettyPrint = false;
        lineBreak = false;
        preferLineBreakAtEndOfFile = false;
        reportPath = null;
        tracer = TracerMode.OFF;
        colorizeErrorOutput = false;
        errorFormat = ErrorFormat.SINGLELINE;
        debugFunctionSideEffectsPath = null;
        externExports = false;
        nameReferenceReportPath = null;
        nameReferenceGraphPath = null;
        // Debugging
        aliasHandler = NULL_ALIAS_TRANSFORMATION_HANDLER;
        errorHandler = null;
    }

    /**
     * Whether the warnings guard in this Options object enables the given
     * group of warnings.
     */
    boolean enables(DiagnosticGroup type) {
        return warningsGuard.enables(type);
    }

    /**
     * Whether the warnings guard in this Options object disables the given
     * group of warnings.
     */
    boolean disables(DiagnosticGroup type) {
        return warningsGuard.disables(type);
    }

    /**
     * Configure the given type of warning to the given level.
     */
    public void setWarningLevel(DiagnosticGroup type, CheckLevel level) {
        addWarningsGuard(new DiagnosticGroupWarningsGuard(type, level));
    }

    WarningsGuard getWarningsGuard() {
        return warningsGuard;
    }

    /**
     * Add a guard to the set of warnings guards.
     */
    public void addWarningsGuard(WarningsGuard guard) {
        warningsGuard.addGuard(guard);
    }

    public boolean shouldColorizeErrorOutput() {
        return colorizeErrorOutput;
    }

    public void setCodingConvention(CodingConvention codingConvention) {
        this.codingConvention = codingConvention;
    }

    public CodingConvention getCodingConvention() {
        return codingConvention;
    }

    /**
     * Sets how goog.tweak calls are processed.
     */
    public void setLanguageIn(LanguageMode languageIn) {
        this.languageIn = languageIn;
        this.languageOut = languageIn;
    }

    public LanguageMode getLanguageIn() {
        return languageIn;
    }

    public LanguageMode getLanguageOut() {
        return languageOut;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        CompilerOptions clone = (CompilerOptions) super.clone();
        // TODO(bolinfest): Add relevant custom cloning.
        return clone;
    }

    //////////////////////////////////////////////////////////////////////////////
    // Enums
    /**
     * When to do the extra sanity checks
     */
    public static enum LanguageMode {

        /**
         * Traditional JavaScript
         */
        ECMASCRIPT3,
        /**
         * Shiny new JavaScript
         */
        ECMASCRIPT5,
        /**
         * Nitpicky, shiny new JavaScript
         */
        ECMASCRIPT5_STRICT
    }

    /**
     * When to do the extra sanity checks
     */
    static enum DevMode {

        /**
         * Don't do any extra sanity checks.
         */
        OFF
    }

    public static enum TracerMode {

        // Collect no timing and size metrics.
        OFF
    }

    public static enum TweakProcessing {

        // Do not run the ProcessTweaks pass.
        OFF
    }

    /**
     * A Role Specific Interface for JS Compiler that represents a data holder
     * object which is used to store goog.scope alias code changes to code made
     * during a compile. There is no guarantee that individual alias changes are
     * invoked in the order they occur during compilation, so implementations
     * should not assume any relevance to the order changes arrive.
     * <p>
     * Calls to the mutators are expected to resolve very quickly, so
     * implementations should not perform expensive operations in the mutator
     * methods.
     *
     * @author tylerg@google.com (Tyler Goodwin)
     */
    public interface AliasTransformationHandler {
    }

    /**
     * A Role Specific Interface for the JS Compiler to report aliases used to
     * change the code during a compile.
     * <p>
     * While aliases defined by goog.scope are expected to by only 1 per file, and
     * the only top-level structure in the file, this is not enforced.
     */
    public interface AliasTransformation {
    }

    /**
     * A Null implementation of the CodeChanges interface which performs all
     * operations as a No-Op
     */
    static final AliasTransformationHandler NULL_ALIAS_TRANSFORMATION_HANDLER = new NullAliasTransformationHandler();

    private static class NullAliasTransformationHandler implements AliasTransformationHandler, Serializable {

        private static final long serialVersionUID = 0L;

        private static final AliasTransformation NULL_ALIAS_TRANSFORMATION = new NullAliasTransformation();

        public AliasTransformation logAliasTransformation(String sourceFile, SourcePosition<AliasTransformation> position) {
            position.setItem(NULL_ALIAS_TRANSFORMATION);
            return NULL_ALIAS_TRANSFORMATION;
        }

        private static class NullAliasTransformation implements AliasTransformation, Serializable {

            private static final long serialVersionUID = 0L;

            public void addAlias(String alias, String definition) {
            }
        }
    }
}
