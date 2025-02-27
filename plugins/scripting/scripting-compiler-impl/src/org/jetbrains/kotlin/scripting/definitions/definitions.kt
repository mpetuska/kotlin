/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.definitions

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.SourceCode

inline fun <T> runReadAction(crossinline runnable: () -> T): T {
    return ApplicationManager.getApplication().runReadAction(Computable { runnable() })
}

fun PsiFile.findScriptDefinition(): ScriptDefinition? {
    // Do not use psiFile.script, see comments in findScriptDefinition
    if (this !is KtFile/* || this.script == null*/) return null

    val virtualFile = this.virtualFile ?: this.originalFile.virtualFile ?: return null
    if (virtualFile.isNonScript()) return null

    return findScriptDefinition(project, KtFileScriptSource(this))
}

@Deprecated("Use PsiFile.findScriptDefinition() instead")
fun VirtualFile.findScriptDefinition(project: Project): ScriptDefinition? {
    if (!isValid || isNonScript()) return null

    // Do not use psiFile.script here because this method can be called during indexes access
    // and accessing stubs may cause deadlock
    // TODO: measure performance effect and if necessary consider detecting indexing here or using separate logic for non-IDE operations to speed up filtering
    if (runReadAction { PsiManager.getInstance(project).findFile(this) as? KtFile }/*?.script*/ == null) return null

    return findScriptDefinition(project, VirtualFileScriptSource(this))
}

fun findScriptDefinition(project: Project, script: SourceCode): ScriptDefinition? {
    val scriptDefinitionProvider = ScriptDefinitionProvider.getInstance(project) ?: return null
        ?: throw IllegalStateException("Unable to get script definition: ScriptDefinitionProvider is not configured.")

    return scriptDefinitionProvider.findDefinition(script) ?: scriptDefinitionProvider.getDefaultDefinition()
}

fun VirtualFile.isNonScript(): Boolean =
    isDirectory ||
            extension == KotlinFileType.EXTENSION ||
            extension == JavaFileType.INSTANCE.defaultExtension ||
            extension == JavaClassFileType.INSTANCE.defaultExtension ||
            !this.isKotlinFileType()

private fun VirtualFile.isKotlinFileType(): Boolean {
    if (extension == KotlinParserDefinition.STD_SCRIPT_SUFFIX) return true

    val typeRegistry = FileTypeRegistry.getInstance()
    return typeRegistry.getFileTypeByFile(this) == KotlinFileType.INSTANCE ||
            typeRegistry.getFileTypeByFileName(name) == KotlinFileType.INSTANCE
}
