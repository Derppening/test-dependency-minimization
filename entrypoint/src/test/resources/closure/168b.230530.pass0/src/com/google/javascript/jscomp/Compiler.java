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
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 * Compiler (and the other classes in this package) does the following:
 * <ul>
 * <li>parses JS code
 * <li>checks for undefined variables
 * <li>performs optimizations such as constant folding and constants inlining
 * <li>renames variables (to short names)
 * <li>outputs compact JavaScript code
 * </ul>
 *
 * External variables are declared in 'externs' files. For instance, the file
 * may include definitions for global javascript/browser objects such as
 * window, document.
 */
public class Compiler extends AbstractCompiler {

    static final String SINGLETON_MODULE_NAME = "[singleton]";

    static final DiagnosticType MODULE_DEPENDENCY_ERROR = DiagnosticType.error("JSC_MODULE_DEPENDENCY_ERROR", "Bad dependency: {0} -> {1}. " + "Modules must be listed in dependency order.");

    static final DiagnosticType MISSING_ENTRY_ERROR = DiagnosticType.error("JSC_MISSING_ENTRY_ERROR", "required entry point \"{0}\" never provided");

    private static final String CONFIG_RESOURCE = "com.google.javascript.jscomp.parsing.ParserConfig";

    CompilerOptions options = null;

    private PassConfig passes = null;

    // The externs inputs
    private List<CompilerInput> externs;

    // The JS source modules
    private List<JSModule> modules;

    // The graph of the JS source modules. Must be null if there are less than
    // 2 modules, because we use this as a signal for which passes to run.
    private JSModuleGraph moduleGraph;

    // The JS source inputs
    private List<CompilerInput> inputs;

    // error manager to which error management is delegated
    private ErrorManager errorManager;

    // Warnings guard for filtering warnings.
    private WarningsGuard warningsGuard;

    // Compile-time injected libraries. The node points to the last node of
    // the library, so code can be inserted after.
    private final Map<String, Node> injectedLibraries = Maps.newLinkedHashMap();

    // Parse tree root nodes
    Node externsRoot;

    private Map<InputId, CompilerInput> inputsById;

    /**
     * The source code map
     */
    private SourceMap sourceMap;

    /**
     * The externs created from the exports.
     */
    private String externExports = null;

    /**
     * Ids for function inlining so that each declared name remains
     * unique.
     */
    private int uniqueNameId = 0;

    /**
     * Whether to assume there are references to the RegExp Global object
     * properties.
     */
    private boolean hasRegExpGlobalReferences = true;

    /**
     * Debugging information
     */
    private final StringBuilder debugLog = new StringBuilder();

    /**
     * Detects Google-specific coding conventions.
     */
    CodingConvention defaultCodingConvention = new ClosureCodingConvention();

    private JSTypeRegistry typeRegistry;

    private Config parserConfig = null;

    private TypeValidator typeValidator;

    // The oldErrorReporter exists so we can get errors from the JSTypeRegistry.
    private final com.google.javascript.rhino.ErrorReporter oldErrorReporter = RhinoErrorReporter.forOldRhino(this);

    // This error reporter gets the messages from the current Rhino parser.
    private final ErrorReporter defaultErrorReporter = RhinoErrorReporter.forNewRhino(this);

    /**
     * Error strings used for reporting JSErrors
     */
    public static final DiagnosticType OPTIMIZE_LOOP_ERROR = DiagnosticType.error("JSC_OPTIMIZE_LOOP_ERROR", "Exceeded max number of optimization iterations: {0}");

    public static final DiagnosticType MOTION_ITERATIONS_ERROR = DiagnosticType.error("JSC_OPTIMIZE_LOOP_ERROR", "Exceeded max number of code motion iterations: {0}");

    // We use many recursive algorithms that use O(d) memory in the depth
    // of the tree.
    // About 2MB
    private static final long COMPILER_STACK_SIZE = (1 << 21);

    /**
     * Under JRE 1.6, the JS Compiler overflows the stack when running on some
     * large or complex JS code. When threads are available, we run all compile
     * jobs on a separate thread with a larger stack.
     *
     * That way, we don't have to increase the stack size for *every* thread
     * (which is what -Xss does).
     *
     * TODO(nicksantos): Add thread pool support for clients that compile a lot.
     */
    private ExecutorService compilerExecutor = Executors.newCachedThreadPool(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(null, r, "jscompiler", COMPILER_STACK_SIZE);
        }
    });

    /**
     * Use a dedicated compiler thread per Compiler instance.
     */
    private Thread compilerThread = null;

    /**
     * Whether to use threads.
     */
    private boolean useThreads = true;

    /**
     * Logger for the whole com.google.javascript.jscomp domain -
     * setting configuration for this logger affects all loggers
     *  in other classes within the compiler.
     */
    private static final Logger logger = Logger.getLogger("com.google.javascript.jscomp");

    private final PrintStream outStream;

    private GlobalVarReferenceMap globalRefMap = null;

    private volatile double progress = 0.0;

    /**
     * Creates a Compiler that reports errors and warnings to its logger.
     */
    public Compiler() {
        this((PrintStream) null);
    }

    /**
     * Creates n Compiler that reports errors and warnings to an output
     * stream.
     */
    public Compiler(PrintStream stream) {
        addChangeHandler(recentChange);
        outStream = stream;
    }

    /**
     * Sets the error manager.
     *
     * @param errorManager the error manager, it cannot be {@code null}
     */
    public void setErrorManager(ErrorManager errorManager) {
        Preconditions.checkNotNull(errorManager, "the error manager cannot be null");
        this.errorManager = errorManager;
    }

    /**
     * Creates a message formatter instance corresponding to the value of
     * {@link CompilerOptions}.
     */
    private MessageFormatter createMessageFormatter() {
        boolean colorize = options.shouldColorizeErrorOutput();
        return options.errorFormat.toFormatter(this, colorize);
    }

    /**
     * Initialize the compiler options. Only necessary if you're not doing
     * a normal compile() job.
     */
    public void initOptions(CompilerOptions options) {
        this.options = options;
        if (errorManager == null) {
            if (outStream == null) {
                setErrorManager(new LoggerErrorManager(createMessageFormatter(), logger));
            } else {
                PrintStreamErrorManager printer = new PrintStreamErrorManager(createMessageFormatter(), outStream);
                printer.setSummaryDetailLevel(options.summaryDetailLevel);
                setErrorManager(printer);
            }
        }
        // DiagnosticGroups override the plain checkTypes option.
        if (options.enables(DiagnosticGroups.CHECK_TYPES)) {
            options.checkTypes = true;
        } else if (options.disables(DiagnosticGroups.CHECK_TYPES)) {
            options.checkTypes = false;
        } else if (!options.checkTypes) {
            // If DiagnosticGroups did not override the plain checkTypes
            // option, and checkTypes is enabled, then turn off the
            // parser type warnings.
            options.setWarningLevel(DiagnosticGroup.forType(RhinoErrorReporter.TYPE_PARSE_ERROR), CheckLevel.OFF);
        }
        if (options.checkGlobalThisLevel.isOn() && !options.disables(DiagnosticGroups.GLOBAL_THIS)) {
            options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, options.checkGlobalThisLevel);
        }
        if (options.getLanguageIn() == LanguageMode.ECMASCRIPT5_STRICT) {
            options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.ERROR);
        }
        // Initialize the warnings guard.
        List<WarningsGuard> guards = Lists.newArrayList();
        guards.add(new SuppressDocWarningsGuard(getDiagnosticGroups().getRegisteredGroups()));
        guards.add(options.getWarningsGuard());
        ComposeWarningsGuard composedGuards = new ComposeWarningsGuard(guards);
        // All passes must run the variable check. This synthesizes
        // variables later so that the compiler doesn't crash. It also
        // checks the externs file for validity. If you don't want to warn
        // about missing variable declarations, we shut that specific
        // error off.
        if (!options.checkSymbols && !composedGuards.enables(DiagnosticGroups.CHECK_VARIABLES)) {
            composedGuards.addGuard(new DiagnosticGroupWarningsGuard(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF));
        }
        this.warningsGuard = composedGuards;
    }

    /**
     * Initializes the instance state needed for a compile job.
     */
    public <T1 extends SourceFile, T2 extends SourceFile> void init(List<T1> externs, List<T2> inputs, CompilerOptions options) {
        JSModule module = new JSModule(SINGLETON_MODULE_NAME);
        for (SourceFile input : inputs) {
            module.add(input);
        }
        initModules(externs, Lists.newArrayList(module), options);
    }

    /**
     * Initializes the instance state needed for a compile job if the sources
     * are in modules.
     */
    public <T extends SourceFile> void initModules(List<T> externs, List<JSModule> modules, CompilerOptions options) {
        initOptions(options);
        checkFirstModule(modules);
        fillEmptyModules(modules);
        this.externs = makeCompilerInput(externs, true);
        // Generate the module graph, and report any errors in the module
        // specification as errors.
        this.modules = modules;
        if (modules.size() > 1) {
            try {
                this.moduleGraph = new JSModuleGraph(modules);
            } catch (JSModuleGraph.ModuleDependenceException e) {
                // problems with the module format.  Report as an error.  The
                // message gives all details.
                report(JSError.make(MODULE_DEPENDENCY_ERROR, e.getModule().getName(), e.getDependentModule().getName()));
                return;
            }
        } else {
            this.moduleGraph = null;
        }
        this.inputs = getAllInputsFromModules(modules);
        initBasedOnOptions();
        initInputsByIdMap();
    }

    /**
     * Do any initialization that is dependent on the compiler options.
     */
    private void initBasedOnOptions() {
        // Create the source map if necessary.
        if (options.sourceMapOutputPath != null) {
            sourceMap = options.sourceMapFormat.getInstance();
            sourceMap.setPrefixMappings(options.sourceMapLocationMappings);
        }
    }

    private <T extends SourceFile> List<CompilerInput> makeCompilerInput(List<T> files, boolean isExtern) {
        List<CompilerInput> inputs = Lists.newArrayList();
        for (T file : files) {
            inputs.add(new CompilerInput(file, isExtern));
        }
        return inputs;
    }

    private static final DiagnosticType EMPTY_MODULE_LIST_ERROR = DiagnosticType.error("JSC_EMPTY_MODULE_LIST_ERROR", "At least one module must be provided");

    private static final DiagnosticType EMPTY_ROOT_MODULE_ERROR = DiagnosticType.error("JSC_EMPTY_ROOT_MODULE_ERROR", "Root module '{0}' must contain at least one source code input");

    /**
     * Verifies that at least one module has been provided and that the first one
     * has at least one source code input.
     */
    private void checkFirstModule(List<JSModule> modules) {
        if (modules.isEmpty()) {
            report(JSError.make(EMPTY_MODULE_LIST_ERROR));
        } else if (modules.get(0).getInputs().isEmpty() && modules.size() > 1) {
            // The root module may only be empty if there is exactly 1 module.
            report(JSError.make(EMPTY_ROOT_MODULE_ERROR, modules.get(0).getName()));
        }
    }

    /**
     * Empty modules get an empty "fill" file, so that we can move code into
     * an empty module.
     */
    static String createFillFileName(String moduleName) {
        return "[" + moduleName + "]";
    }

    /**
     * Fill any empty modules with a place holder file. It makes any cross module
     * motion easier.
     */
    private static void fillEmptyModules(List<JSModule> modules) {
        for (JSModule module : modules) {
            if (module.getInputs().isEmpty()) {
                module.add(SourceFile.fromCode(createFillFileName(module.getName()), ""));
            }
        }
    }

    /**
     * Builds a single list of all module inputs. Verifies that it contains no
     * duplicates.
     */
    private static List<CompilerInput> getAllInputsFromModules(List<JSModule> modules) {
        List<CompilerInput> inputs = Lists.newArrayList();
        Map<String, JSModule> inputMap = Maps.newHashMap();
        for (JSModule module : modules) {
            for (CompilerInput input : module.getInputs()) {
                String inputName = input.getName();
                // NOTE(nicksantos): If an input is in more than one module,
                // it will show up twice in the inputs list, and then we
                // will get an error down the line.
                inputs.add(input);
                inputMap.put(inputName, module);
            }
        }
        return inputs;
    }

    static final DiagnosticType DUPLICATE_INPUT = DiagnosticType.error("JSC_DUPLICATE_INPUT", "Duplicate input: {0}");

    static final DiagnosticType DUPLICATE_EXTERN_INPUT = DiagnosticType.error("JSC_DUPLICATE_EXTERN_INPUT", "Duplicate extern input: {0}");

    /**
     * Creates a map to make looking up an input by name fast. Also checks for
     * duplicate inputs.
     */
    void initInputsByIdMap() {
        inputsById = new HashMap<InputId, CompilerInput>();
        for (CompilerInput input : externs) {
            InputId id = input.getInputId();
            CompilerInput previous = putCompilerInput(id, input);
            if (previous != null) {
                report(JSError.make(DUPLICATE_EXTERN_INPUT, input.getName()));
            }
        }
        for (CompilerInput input : inputs) {
            InputId id = input.getInputId();
            CompilerInput previous = putCompilerInput(id, input);
            if (previous != null) {
                report(JSError.make(DUPLICATE_INPUT, input.getName()));
            }
        }
    }

    private final PassFactory sanityCheck = new PassFactory("sanityCheck", false) {
    };

    private Tracer currentTracer = null;

    private String currentPassName = null;

    /**
     * Returns the array of errors (never null).
     */
    public JSError[] getErrors() {
        return errorManager.getErrors();
    }

    /**
     * Returns the array of warnings (never null).
     */
    public JSError[] getWarnings() {
        return errorManager.getWarnings();
    }

    /**
     * Creates a new id for making unique names.
     */
    private int nextUniqueNameId() {
        return uniqueNameId++;
    }

    Supplier<String> getUniqueNameIdSupplier() {
        final Compiler self = this;
        return new Supplier<String>() {

            @Override
            public String get() {
                return String.valueOf(self.nextUniqueNameId());
            }
        };
    }

    boolean areNodesEqualForInlining(Node n1, Node n2) {
        if (options.ambiguateProperties || options.disambiguateProperties) {
            // The type based optimizations require that type information is preserved
            // during other optimizations.
            return n1.isEquivalentToTyped(n2);
        } else {
            return n1.isEquivalentTo(n2);
        }
    }

    //------------------------------------------------------------------------
    // Inputs
    //------------------------------------------------------------------------
    // TODO(nicksantos): Decide which parts of these belong in an AbstractCompiler
    // interface, and which ones should always be injected.
    @Override
    public CompilerInput getInput(InputId id) {
        return inputsById.get(id);
    }

    public CompilerInput newExternInput(String name) {
        SourceAst ast = new SyntheticAst(name);
        if (inputsById.containsKey(ast.getInputId())) {
            throw new IllegalArgumentException("Conflicting externs name: " + name);
        }
        CompilerInput input = new CompilerInput(ast, true);
        putCompilerInput(input.getInputId(), input);
        externsRoot.addChildToFront(ast.getAstRoot(this));
        externs.add(0, input);
        return input;
    }

    private CompilerInput putCompilerInput(InputId id, CompilerInput input) {
        input.setCompiler(this);
        return inputsById.put(id, input);
    }

    JSModuleGraph getModuleGraph() {
        return moduleGraph;
    }

    @Override
    public JSTypeRegistry getTypeRegistry() {
        if (typeRegistry == null) {
            typeRegistry = new JSTypeRegistry(oldErrorReporter, options.looseTypes);
        }
        return typeRegistry;
    }

    @Override
    TypeValidator getTypeValidator() {
        if (typeValidator == null) {
            typeValidator = new TypeValidator(this);
        }
        return typeValidator;
    }

    //------------------------------------------------------------------------
    // Parsing
    //------------------------------------------------------------------------
    private int syntheticCodeId = 0;

    Node parseSyntheticCode(String js) {
        CompilerInput input = new CompilerInput(SourceFile.fromCode(" [synthetic:" + (++syntheticCodeId) + "] ", js));
        putCompilerInput(input.getInputId(), input);
        return input.getAstRoot(this);
    }

    /**
     * Allow subclasses to override the default CompileOptions object.
     */
    protected CompilerOptions newCompilerOptions() {
        return new CompilerOptions();
    }

    void initCompilerOptionsIfTesting() {
        if (options == null) {
            // initialization for tests that don't initialize the compiler
            // by the normal mechanisms.
            initOptions(newCompilerOptions());
        }
    }

    @Override
    ErrorReporter getDefaultErrorReporter() {
        return defaultErrorReporter;
    }

    //------------------------------------------------------------------------
    // Convert back to source code
    //------------------------------------------------------------------------
    /**
     * Generates JavaScript source code for an AST, doesn't generate source
     * map info.
     */
    String toSource(Node n) {
        initCompilerOptionsIfTesting();
        return toSource(n, null, true);
    }

    /**
     * Generates JavaScript source code for an AST.
     */
    private String toSource(Node n, SourceMap sourceMap, boolean firstOutput) {
        CodePrinter.Builder builder = new CodePrinter.Builder(n);
        builder.setPrettyPrint(options.prettyPrint);
        builder.setLineBreak(options.lineBreak);
        builder.setPreferLineBreakAtEndOfFile(options.preferLineBreakAtEndOfFile);
        builder.setSourceMap(sourceMap);
        builder.setSourceMapDetailLevel(options.sourceMapDetailLevel);
        builder.setTagAsStrict(firstOutput && options.getLanguageOut() == LanguageMode.ECMASCRIPT5_STRICT);
        builder.setLineLengthThreshold(options.lineLengthThreshold);
        Charset charset = options.outputCharset != null ? Charset.forName(options.outputCharset) : null;
        builder.setOutputCharset(charset);
        return builder.build();
    }

    //------------------------------------------------------------------------
    // Optimizations
    //------------------------------------------------------------------------
    void setCssRenamingMap(CssRenamingMap map) {
        options.cssRenamingMap = map;
    }

    @Override
    void prepareAst(Node root) {
        CompilerPass pass = new PrepareAst(this);
        pass.process(null, root);
    }

    protected final CodeChangeHandler.RecentChange recentChange = new CodeChangeHandler.RecentChange();

    private final List<CodeChangeHandler> codeChangeHandlers = Lists.<CodeChangeHandler>newArrayList();

    /**
     * Name of the synthetic input that holds synthesized externs.
     */
    static final String SYNTHETIC_EXTERNS = "{SyntheticVarsDeclar}";

    private CompilerInput synthesizedExternsInput = null;

    void addChangeHandler(CodeChangeHandler handler) {
        codeChangeHandlers.add(handler);
    }

    void removeChangeHandler(CodeChangeHandler handler) {
        codeChangeHandlers.remove(handler);
    }

    /**
     * All passes should call reportCodeChange() when they alter
     * the JS tree structure. This is verified by CompilerTestCase.
     * This allows us to optimize to a fixed point.
     */
    public void reportCodeChange() {
        for (CodeChangeHandler handler : codeChangeHandlers) {
            handler.reportChange();
        }
    }

    @Override
    public CodingConvention getCodingConvention() {
        CodingConvention convention = options.getCodingConvention();
        convention = convention != null ? convention : defaultCodingConvention;
        return convention;
    }

    @Override
    public boolean isIdeMode() {
        return options.ideMode;
    }

    public boolean acceptEcmaScript5() {
        switch(options.getLanguageIn()) {
            case ECMASCRIPT5:
            case ECMASCRIPT5_STRICT:
                return true;
        }
        return false;
    }

    public boolean acceptConstKeyword() {
        return options.acceptConstKeyword;
    }

    @Override
    Config getParserConfig() {
        if (parserConfig == null) {
            Config.LanguageMode mode;
            switch(options.getLanguageIn()) {
                case ECMASCRIPT3:
                    mode = Config.LanguageMode.ECMASCRIPT3;
                    break;
                case ECMASCRIPT5:
                    mode = Config.LanguageMode.ECMASCRIPT5;
                    break;
                case ECMASCRIPT5_STRICT:
                    mode = Config.LanguageMode.ECMASCRIPT5_STRICT;
                    break;
                default:
                    throw new IllegalStateException("unexpected language mode");
            }
            parserConfig = ParserRunner.createConfig(isIdeMode(), mode, acceptConstKeyword(), options.extraAnnotationNames);
        }
        return parserConfig;
    }

    //------------------------------------------------------------------------
    // Error reporting
    //------------------------------------------------------------------------
    /**
     * The warning classes that are available from the command-line, and
     * are suppressible by the {@code @suppress} annotation.
     */
    protected DiagnosticGroups getDiagnosticGroups() {
        return new DiagnosticGroups();
    }

    @Override
    public void report(JSError error) {
        CheckLevel level = error.getDefaultLevel();
        if (warningsGuard != null) {
            CheckLevel newLevel = warningsGuard.level(error);
            if (newLevel != null) {
                level = newLevel;
            }
        }
        if (level.isOn()) {
            if (getOptions().errorHandler != null) {
                getOptions().errorHandler.report(level, error);
            }
            errorManager.report(level, error);
        }
    }

    @Override
    public CheckLevel getErrorLevel(JSError error) {
        Preconditions.checkNotNull(options);
        return warningsGuard.level(error);
    }

    /**
     * Report an internal error.
     */
    @Override
    void throwInternalError(String message, Exception cause) {
        String finalMessage = "INTERNAL COMPILER ERROR.\n" + "Please report this problem.\n" + message;
        RuntimeException e = new RuntimeException(finalMessage, cause);
        if (cause != null) {
            e.setStackTrace(cause.getStackTrace());
        }
        throw e;
    }

    /**
     * Gets the number of errors.
     */
    public int getErrorCount() {
        return errorManager.getErrorCount();
    }

    @Override
    boolean hasHaltingErrors() {
        return !isIdeMode() && getErrorCount() > 0;
    }

    /**
     * Called from the compiler passes, adds debug info
     */
    void addToDebugLog(String str) {
        debugLog.append(str);
        debugLog.append('\n');
        logger.fine(str);
    }

    @Override
    SourceFile getSourceFileByName(String sourceName) {
        // Here we assume that the source name is the input name, this
        // is try of JavaScript parsed from source.
        if (sourceName != null) {
            CompilerInput input = inputsById.get(new InputId(sourceName));
            if (input != null) {
                return input.getSourceFile();
            }
        }
        return null;
    }

    @Override
    public String getSourceLine(String sourceName, int lineNumber) {
        if (lineNumber < 1) {
            return null;
        }
        SourceFile input = getSourceFileByName(sourceName);
        if (input != null) {
            return input.getLine(lineNumber);
        }
        return null;
    }

    public Region getSourceRegion(String sourceName, int lineNumber) {
        if (lineNumber < 1) {
            return null;
        }
        SourceFile input = getSourceFileByName(sourceName);
        if (input != null) {
            return input.getRegion(lineNumber);
        }
        return null;
    }

    //------------------------------------------------------------------------
    // Package-private helpers
    //------------------------------------------------------------------------
    Node getNodeForCodeInsertion(JSModule module) {
        if (module == null) {
            if (inputs.isEmpty()) {
                throw new IllegalStateException("No inputs");
            }
            return inputs.get(0).getAstRoot(this);
        }
        List<CompilerInput> moduleInputs = module.getInputs();
        if (moduleInputs.size() > 0) {
            return moduleInputs.get(0).getAstRoot(this);
        }
        throw new IllegalStateException("Root module has no inputs");
    }

    CompilerOptions getOptions() {
        return options;
    }

    @Override
    public ErrorManager getErrorManager() {
        if (options == null) {
            initOptions(newCompilerOptions());
        }
        return errorManager;
    }

    List<CompilerInput> getInputsInOrder() {
        return Collections.<CompilerInput>unmodifiableList(inputs);
    }

    /**
     * Gets the externs in the order in which they are being processed.
     */
    List<CompilerInput> getExternsInOrder() {
        return Collections.<CompilerInput>unmodifiableList(externs);
    }

    @Override
    boolean hasRegExpGlobalReferences() {
        return hasRegExpGlobalReferences;
    }

    void updateGlobalVarReferences(Map<Var, ReferenceCollection> refMapPatch, Node collectionRoot) {
        Preconditions.checkState(collectionRoot.isScript() || collectionRoot.isBlock());
        if (globalRefMap == null) {
            globalRefMap = new GlobalVarReferenceMap(getInputsInOrder(), getExternsInOrder());
        }
        globalRefMap.updateGlobalVarReferences(refMapPatch, collectionRoot);
    }

    GlobalVarReferenceMap getGlobalVarReferences() {
        return globalRefMap;
    }

    CompilerInput getSynthesizedExternsInput() {
        if (synthesizedExternsInput == null) {
            synthesizedExternsInput = newExternInput(SYNTHETIC_EXTERNS);
        }
        return synthesizedExternsInput;
    }
}
