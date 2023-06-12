package edu.washington.cs.mut.testrunner

import org.junit.runner.JUnitCore
import org.junit.runner.Request
import kotlin.system.exitProcess

object SingleTestRunner {

    private fun usageAndExit(): Nothing {
        System.err.println("usage: java ${SingleTestRunner::class.java.name} testClass[::testMethod]")
        exitProcess(1)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 1) {
            usageAndExit()
        }

        val m = Regex("(?<className>[^:]+)(::(?<methodName>[^:]+))?").matchEntire(args[0])
            ?: usageAndExit()

        val className = checkNotNull(m.groups["className"]).value
        val clazz = runCatching {
            Class.forName(className)
        }.onFailure { e ->
            System.err.println("Couldn't load class ($className): ${e.message}")
            exitProcess(1)
        }.getOrThrow()

        val methodName = m.groups["methodName"]?.value

        val req = if (methodName == null) {
            Request.aClass(clazz)
        } else {
            Request.method(clazz, methodName)
        }

        val res = JUnitCore().run(req)
        if (!res.wasSuccessful()) {
            System.err.println("Test failed!")
            res.failures.forEach { f ->
                System.err.println(f.toString())
            }
            exitProcess(2)
        }
        exitProcess(0)
    }
}