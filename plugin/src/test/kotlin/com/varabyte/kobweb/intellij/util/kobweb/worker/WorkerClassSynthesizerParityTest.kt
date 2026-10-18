package com.varabyte.kobweb.intellij.util.kobweb.worker

import com.varabyte.kobweb.intellij.test.fixtures.KobwebWorkerTestCase
import com.varabyte.truthish.assertThat
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.Variance
import java.io.File
import java.util.Properties

/**
 * Verify that the `Worker` class we synthesize has the same public API as the one Kobweb's real KSP processor
 * generates.
 *
 * Each test case is a folder under `src/test/testData/worker/cases`, containing worker sources and the golden output
 * that Kobweb's processor generated for them (see the README in that folder for how to regenerate it). Each test loads
 * the golden output into the test project under a different package, and then compares it to our synthesized class.
 */
class WorkerClassSynthesizerParityTest : KobwebWorkerTestCase() {
    private val casesDir by lazy { filesFromProperty("kobweb.test.worker.cases").single() }

    private class GeneratedClass(val classId: ClassId, val source: String)

    /**
     * A worker test case, loaded from a folder of test data.
     *
     * @property sources A map of relative file paths to their contents.
     * @property fqcnOverride The value of `kobweb.worker.fqcn` used to generate this case, if any.
     * @property generated The worker class that Kobweb's processor generated for this case.
     */
    private class WorkerCase(val sources: Map<String, String>, val fqcnOverride: String?, val generated: GeneratedClass)

    private fun loadCase(name: String): WorkerCase {
        val caseDir = casesDir.resolve(name)
        fun File.readKotlinFiles(): Map<String, String> = walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(this).invariantSeparatorsPath to it.readText() }

        val fqcnOverride = caseDir.resolve("case.properties").takeIf { it.exists() }
            ?.let { file -> Properties().apply { file.reader().use { load(it) } }.getProperty("fqcn") }

        // The expected folder only contains the worker class, at a path matching its package
        val (generatedPath, generatedSource) = caseDir.resolve("expected").readKotlinFiles().entries.single()
        val generatedClassId = ClassId(
            FqName(generatedPath.substringBeforeLast('/', missingDelimiterValue = "").replace('/', '.')),
            Name.identifier(generatedPath.substringAfterLast('/').removeSuffix(".kt")),
        )

        return WorkerCase(
            caseDir.resolve("src").readKotlinFiles(),
            fqcnOverride,
            GeneratedClass(generatedClassId, generatedSource),
        )
    }

    /**
     * Render the public API of a class, using fully qualified type names.
     *
     * The class's own name is intentionally left out, so classes in different packages can be compared.
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.renderPublicApi(classSymbol: KaClassSymbol): List<String> {
        fun KaType.render() = render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)

        return buildList {
            add("class: ${classSymbol.classKind} ${classSymbol.modality} ${classSymbol.visibility}")
            classSymbol.superTypes.forEach { add("supertype: ${it.render()}") }
            classSymbol.declaredMemberScope.constructors
                .filter { it.visibility == KaSymbolVisibility.PUBLIC }
                .forEach { constructor ->
                    add("constructor(${constructor.valueParameters.joinToString { param ->
                        "${param.name}: ${param.returnType.render()}${if (param.hasDefaultValue) " = <default>" else ""}"
                    }})")
                }
            classSymbol.declaredMemberScope.callables
                .filter { it.visibility == KaSymbolVisibility.PUBLIC }
                .forEach { callable ->
                    when (callable) {
                        is KaPropertySymbol -> add(
                            "${if (callable.isVal) "val" else "var"} ${callable.name}: ${callable.returnType.render()}"
                        )
                        is KaNamedFunctionSymbol -> add(
                            "fun ${callable.name}(${callable.valueParameters.joinToString { param ->
                                "${param.name}: ${param.returnType.render()}"
                            }}): ${callable.returnType.render()}"
                        )
                        else -> add("unexpected: $callable")
                    }
                }
        }.sorted()
    }

    /**
     * Load the target test case and verify our synthesized worker class matches what Kobweb's processor generated.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun assertParity(caseName: String) {
        val case = loadCase(caseName)
        val generated = case.generated

        setWorkerFqcn(case.fqcnOverride)
        case.sources.forEach { (path, text) -> myFixture.addFileToProject(path, text) }

        // Move the generated class into its own package, so it doesn't conflict with our synthesized version
        val generatedPackage = FqName("kspoutput").let { root ->
            if (generated.classId.packageFqName.isRoot) root else root.child(generated.classId.packageFqName)
        }
        val generatedSource = if (generated.classId.packageFqName.isRoot) {
            "package $generatedPackage\n\n${generated.source}"
        } else {
            generated.source.replaceFirst("package ${generated.classId.packageFqName}", "package $generatedPackage")
        }
        val generatedFile = myFixture.addFileToProject("kspoutput/${generated.classId.shortClassName}.kt", generatedSource) as KtFile

        allowAnalysisOnEdt {
            analyze(generatedFile) {
                val generatedClass = findClass(ClassId(generatedPackage, generated.classId.shortClassName))
                assertThat(generatedClass).isNotNull()
                // This also verifies that we picked the same name as the processor did
                val synthesizedClass = findClass(generated.classId)
                assertThat(synthesizedClass).isNotNull()

                assertThat(renderPublicApi(synthesizedClass!!)).containsExactly(renderPublicApi(generatedClass!!)).inOrder()
            }
        }
    }

    private fun FqName.child(relative: FqName) = FqName("${asString()}.${relative.asString()}")

    fun testDefaultName() = assertParity("default-name")
    fun testFqcnOverride() = assertParity("fqcn-override")
    fun testFqcnOverrideWithRelativeClassName() = assertParity("fqcn-override-with-relative-class-name")
    fun testFqcnOverrideWithPackageOnly() = assertParity("fqcn-override-with-package-only")
    fun testFqcnOverrideWithRelativePackage() = assertParity("fqcn-override-with-relative-package")
    fun testFqcnOverrideWithRelativePackageOnly() = assertParity("fqcn-override-with-relative-package-only")
    fun testFqcnOverrideWithAnyFactoryName() = assertParity("fqcn-override-with-any-factory-name")
    fun testFactoryInRootPackage() = assertParity("factory-in-root-package")
    fun testTypesInFactoryPackage() = assertParity("types-in-factory-package")
    fun testFullyQualifiedTypes() = assertParity("fully-qualified-types")
}
