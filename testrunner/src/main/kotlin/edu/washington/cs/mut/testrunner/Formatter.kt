package edu.washington.cs.mut.testrunner

import junit.framework.AssertionFailedError
import junit.framework.Test
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream

class Formatter : JUnitResultFormatter {

    private val ps: PrintStream
    private val allTests: PrintStream

    private lateinit var className: String
    private var alreadyPrinted: Boolean

    init {
        try {
            ps = PrintStream(FileOutputStream(System.getProperty("OUTFILE", "failing-tests.txt"), true), true)
            allTests = PrintStream(FileOutputStream(System.getProperty("ALLTESTS", "all_tests"), true), true)
        } catch (e: FileNotFoundException) {
            throw RuntimeException(e)
        }
        alreadyPrinted = true
    }

    override fun endTestSuite(suite: JUnitTest) {}
    override fun setOutput(out: OutputStream) {}
    override fun setSystemError(err: String) {}
    override fun setSystemOutput(out: String)  {}

    override fun startTestSuite(suite: JUnitTest) {
        className = suite.name
        alreadyPrinted = false
    }

    override fun addError(test: Test, e: Throwable) {
        handle(test, e)
    }

    override fun addFailure(test: Test, e: AssertionFailedError) {
        handle(test, e as Throwable)
    }

    private fun handle(test: Test?, t: Throwable) {
        val prefix = "--- "

        if (test == null) {
            failClass(t, prefix)
            return
        }

        var className = test::class.java.name

        var methodName: String? = null
        var regexp = Regex("(.*)\\((.*)\\)\\s*")
        var match = regexp.matchEntire(test.toString())
        if (match != null) {
            if (className == "junit.framework.JUnit4TestCaseFacade") {
                className = match.groupValues[1]
            }
            methodName = match.groupValues[2]
        }

        regexp = Regex("(.*):(.*)\\s*")
        match = regexp.matchEntire(test.toString())
        if (match != null) {
            className = match.groupValues[1]
            methodName = match.groupValues[2]
        }

        if (methodName == "warning" || methodName == "initializationError") {
            failClass(t, prefix)
        } else if (methodName != null) {
            if (isJunit4InitFail(t)) {
                failClass(t, prefix)
            } else {
                ps.println("${prefix}$className::$methodName")
                t.printStackTrace(ps)
            }
        } else {
            ps.print("${prefix}broken test input $test")
            t.printStackTrace(ps)
        }
    }

    private fun failClass(t: Throwable, prefix: String) {
        if (!alreadyPrinted) {
            ps.println("$prefix$className")
            t.printStackTrace(ps)
            alreadyPrinted = true
        }
    }

    private fun isJunit4InitFail(t: Throwable): Boolean {
        return t.stackTrace.any { it.methodName == "createTest" }
    }

    override fun endTest(test: Test) {}

    override fun startTest(test: Test) {
        allTests.println(test)
    }
}
