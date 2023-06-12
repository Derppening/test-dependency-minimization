package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.util.Logger
import javassist.bytecode.Descriptor
import java.nio.file.Path
import kotlin.io.path.Path

data class BytecodeMethod(
    val className: String,
    val methodName: String,
    val jvmDescriptor: String,
    val reachable: Boolean,
    val fileName: Path? = null
) {

    /**
     * The list of bytecode fully qualified names of parameter types.
     */
    val paramTypes = Descriptor.toString(jvmDescriptor)
        .removeSurrounding("(", ")")
        .split(',')
        .filter { it.isNotBlank() }
    val returnType = Descriptor.toString(jvmDescriptor.substringAfterLast(')'))
    val signature = Descriptor.toString(jvmDescriptor).replace(",", ", ")

    override fun toString(): String = "$className.$methodName$signature"

    companion object {

        private val LOGGER = Logger<BytecodeMethod>()

        fun fromCoberturaCoverage(
            classCov: CoberturaXML.Class,
            methodCoberturaCov: CoberturaXML.Method,
            methodJacocoCov: JacocoXML.Method?
        ): BytecodeMethod {
            if (methodJacocoCov?.let { it.name != methodCoberturaCov.name || it.desc != methodCoberturaCov.signature } == true) {
                val warnMsg = buildString {
                    append("Cobertura and Jacoco method name/signature mismatch! ")
                    append("Cobertura: ")
                    append(methodCoberturaCov.name)
                    append(' ')
                    append(methodCoberturaCov.signature)
                    append(" / Jacoco: ")
                    append(methodJacocoCov.name)
                    append(' ')
                    append(methodJacocoCov.desc)
                }

                LOGGER.warn(warnMsg)
            }

            val reachable = methodCoberturaCov.lineRate > 0.0 ||
                    methodJacocoCov?.let { cov ->
                        cov.counters
                            .single { it.type == JacocoXML.CounterType.INSTRUCTION }
                            .covered > 0
                    } ?: false

            return BytecodeMethod(
                classCov.name,
                methodCoberturaCov.name,
                methodCoberturaCov.signature,
                reachable,
                classCov.filename
            )
        }

        fun fromJacocoCoverage(
            packageCov: JacocoXML.Package,
            classCov: JacocoXML.Class,
            methodJacocoCov: JacocoXML.Method,
            methodCoberturaCov: CoberturaXML.Method?
        ): BytecodeMethod {
            if (methodCoberturaCov?.let { it.name != methodJacocoCov.name || it.signature != methodJacocoCov.desc } == true) {
                val warnMsg = buildString {
                    append("Cobertura and Jacoco method name/signature mismatch! ")
                    append("Cobertura: ")
                    append(methodCoberturaCov.name)
                    append(' ')
                    append(methodCoberturaCov.signature)
                    append(" / Jacoco: ")
                    append(methodJacocoCov.name)
                    append(' ')
                    append(methodJacocoCov.desc)
                }

                LOGGER.warn(warnMsg)
            }

            val reachable = methodJacocoCov
                .counters
                .single { it.type == JacocoXML.CounterType.INSTRUCTION }
                .covered > 0
                    || methodCoberturaCov?.lineRate?.let { it > 0.0 } ?: false

            return BytecodeMethod(
                classCov.name.replace('/', '.'),
                methodJacocoCov.name,
                methodJacocoCov.desc,
                reachable,
                Path(packageCov.name).resolve(classCov.sourceFileName)
            )
        }

        fun fromString(str: String, reachable: Boolean): BytecodeMethod {
            val (qualifiedName, params) = str.let { s ->
                s.takeWhile { it != '(' } to s.takeLastWhile { it != '(' }.dropLast(1).split(", ")
            }
            val (qualifiedClassName, methodName) = qualifiedName.split('.').let { components ->
                components.dropLast(1).joinToString(".") to components.last()
            }

            return BytecodeMethod(
                qualifiedClassName,
                methodName,
                params.joinToString(prefix = "(", postfix = ")") { Descriptor.of(it) },
                reachable
            )
        }
    }
}