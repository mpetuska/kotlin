/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20

import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.VariantMappedCompilationDetails
import java.util.concurrent.Callable

internal open class GradleKpmAwareTargetConfigurator<T : KotlinTarget>(
    private val legacyModelTargetConfigurator: AbstractKotlinTargetConfigurator<T>,
) : AbstractKotlinTargetConfigurator<T>(true, legacyModelTargetConfigurator.createDefaultSourceSets),
    KotlinTargetConfigurator<T> by legacyModelTargetConfigurator {
    // NB: this override enforces calls to other overridden functions; without it, the delegate would call its own ones
    override fun configureTarget(target: T) {
        super<AbstractKotlinTargetConfigurator>.configureTarget(target)
        doKpmSpecificConfigurationSteps(target)
    }

    protected open fun doKpmSpecificConfigurationSteps(target: T) {
        setupMavenPublicationDsl(target)
    }

    private fun setupMavenPublicationDsl(target: T) {
        if (target is AbstractKotlinTarget) {
            target.compilations.all { compilation ->
                val compilationDetails = (compilation as? AbstractKotlinCompilation)?.compilationDetails
                if (compilationDetails is VariantMappedCompilationDetails<*> && compilationDetails.variant.containingModule.isMain) {
                    val variant = compilationDetails.variant
                    if (variant is GradleKpmSingleMavenPublishedModuleHolder) {
                        variant.whenPublicationAssigned { publication ->
                            target.publicationConfigureActions.all { action ->
                                action.execute(publication)
                            }
                        }
                    }
                }
            }
        }
    }


    override fun defineConfigurationsForTarget(target: T) = Unit // done in KPM

    override fun configureCompilationDefaults(target: T) {
        // everything else is done in KPM, but KPM doesn't have resources processing yet
        target.compilations.all { compilation ->
            if (compilation is KotlinCompilationWithResources<*>) {
                configureResourceProcessing(
                    compilation,
                    target.project.files(Callable { compilation.allKotlinSourceSets.map { it.resources } })
                )
            }
        }
    }

    override fun configureCompilations(target: T) {
        target.compilations.maybeCreate(KotlinCompilation.MAIN_COMPILATION_NAME)
        if (legacyModelTargetConfigurator.createTestCompilation) {
            target.compilations.maybeCreate(KotlinCompilation.TEST_COMPILATION_NAME)
        }
    }

    override fun configureArchivesAndComponent(target: T) = Unit // done in KPM

    override fun configureBuild(target: T) {
        legacyModelTargetConfigurator.configureBuild(target)
    }

    override fun configureSourceSet(target: T) {
        legacyModelTargetConfigurator.configureSourceSet(target)
    }
}

internal class GradleKpmAwareTargetWithTestsConfigurator<R : KotlinTargetTestRun<*>, T : KotlinTargetWithTests<*, R>, C>(
    private val legacyModelTargetWithTestsConfigurator: C
) :
    GradleKpmAwareTargetConfigurator<T>(legacyModelTargetWithTestsConfigurator),
    KotlinTargetWithTestsConfigurator<R, T>

        where C : AbstractKotlinTargetConfigurator<T>,
              C : KotlinTargetWithTestsConfigurator<R, T> {

    override fun configureTarget(target: T) {
        super<KotlinTargetWithTestsConfigurator>.configureTarget(target)
        doKpmSpecificConfigurationSteps(target)
    }

    override val testRunClass: Class<R>
        get() = legacyModelTargetWithTestsConfigurator.testRunClass

    override fun createTestRun(name: String, target: T): R =
        legacyModelTargetWithTestsConfigurator.createTestRun(name, target)
}
