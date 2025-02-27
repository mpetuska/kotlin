/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.konan.diagnostics

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object NativeObjCNameChecker : DeclarationChecker {
    private val objCNameFqName = FqName("kotlin.native.ObjCName")

    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        checkDeclaration(declaration, descriptor, context)
        checkOverrides(declaration, descriptor, context)
        checkFakeOverrides(declaration, descriptor, context)
    }

    private fun checkDeclaration(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        val objCNames = descriptor.getObjCNames().filterNotNull()
        if (objCNames.isEmpty()) return
        if (descriptor is CallableMemberDescriptor && descriptor.overriddenDescriptors.isNotEmpty()) {
            objCNames.forEach {
                val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(it.annotation) ?: declaration
                context.trace.report(ErrorsNative.INAPPLICABLE_OBJC_NAME.on(reportLocation))
            }
        }
        objCNames.forEach { checkObjCName(it, declaration, descriptor, context) }
    }

    // We only allow valid ObjC identifiers (even for Swift names)
    private val validFirstChars = ('A'..'Z').toSet() + ('a'..'z').toSet() + '_'
    private val validChars = validFirstChars + ('0'..'9').toSet()

    private fun checkObjCName(
        objCName: ObjCName,
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        val annotationSource = DescriptorToSourceUtils.getSourceFromAnnotation(objCName.annotation)
        annotationSource?.valueArguments?.forEach {
            // We don't support constant references since that would require resolution in ObjCExportLazy
            val expression = it.getArgumentExpression() ?: return@forEach
            if (expression is KtConstantExpression || expression is KtStringTemplateExpression ||
                (it is KtValueArgument && it.stringTemplateExpression != null)
            ) return@forEach
            context.trace.report(ErrorsNative.NON_LITERAL_OBJC_NAME_ARG.on(expression))
        }
        val reportLocation = annotationSource ?: declaration
        if (objCName.name == null && objCName.swiftName == null) {
            context.trace.report(ErrorsNative.INVALID_OBJC_NAME.on(reportLocation))
        }
        val invalidNameFirstChar = objCName.name?.firstOrNull()?.takeUnless(validFirstChars::contains)
        val invalidSwiftNameFirstChar = objCName.swiftName?.firstOrNull()?.takeUnless(validFirstChars::contains)
        val invalidFirstChars = setOfNotNull(invalidNameFirstChar, invalidSwiftNameFirstChar)
        if (invalidFirstChars.isNotEmpty()) {
            context.trace.report(ErrorsNative.INVALID_OBJC_NAME_FIRST_CHAR.on(reportLocation, invalidFirstChars.joinToString("")))
        }
        val invalidNameChars = objCName.name?.toSet()?.subtract(validChars) ?: emptySet()
        val invalidSwiftNameChars = objCName.swiftName?.toSet()?.subtract(validChars) ?: emptySet()
        val invalidChars = invalidNameChars + invalidSwiftNameChars
        if (invalidChars.isNotEmpty()) {
            context.trace.report(ErrorsNative.INVALID_OBJC_NAME_CHARS.on(reportLocation, invalidChars.joinToString("")))
        }
        if (objCName.exact && (descriptor !is ClassDescriptor || descriptor.kind == ClassKind.ENUM_ENTRY)) {
            context.trace.report(ErrorsNative.INAPPLICABLE_EXACT_OBJC_NAME.on(reportLocation))
        }
        if (objCName.exact && objCName.name == null) {
            context.trace.report(ErrorsNative.MISSING_EXACT_OBJC_NAME.on(reportLocation))
        }
    }

    private fun checkOverrides(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is CallableMemberDescriptor || descriptor.overriddenDescriptors.isEmpty()) return
        val objCNames = descriptor.overriddenDescriptors.map { it.getFirstBaseDescriptor().getObjCNames() }
        if (!objCNames.allNamesEquals()) {
            val containingDeclarations = descriptor.overriddenDescriptors.map { it.containingDeclaration }
            context.trace.report(ErrorsNative.INCOMPATIBLE_OBJC_NAME_OVERRIDE.on(declaration, descriptor, containingDeclarations))
        }
    }

    private fun checkFakeOverrides(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is ClassDescriptor) return
        descriptor.defaultType.memberScope
            .getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.Companion.ALL_NAME_FILTER)
            .forEach {
                if (it !is CallableMemberDescriptor || it.kind.isReal) return@forEach
                checkOverrides(declaration, it, context)
            }
    }

    private fun CallableMemberDescriptor.getFirstBaseDescriptor(): CallableMemberDescriptor =
        if (overriddenDescriptors.isEmpty()) this else overriddenDescriptors.first().getFirstBaseDescriptor()

    private class ObjCName(
        val annotation: AnnotationDescriptor
    ) {
        val name: String? = annotation.argumentValue("name")?.value?.safeAs<String>()?.takeIf { it.isNotBlank() }
        val swiftName: String? = annotation.argumentValue("swiftName")?.value?.safeAs<String>()?.takeIf { it.isNotBlank() }
        val exact: Boolean = annotation.argumentValue("exact")?.value?.safeAs<Boolean>() ?: false

        override fun equals(other: Any?): Boolean =
            other is ObjCName && name == other.name && swiftName == other.swiftName && exact == other.exact

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + swiftName.hashCode()
            result = 31 * result + exact.hashCode()
            return result
        }
    }

    private fun DeclarationDescriptor.getObjCName(): ObjCName? = annotations.findAnnotation(objCNameFqName)?.let(::ObjCName)

    private fun DeclarationDescriptor.getObjCNames(): List<ObjCName?> = when (this) {
        is FunctionDescriptor -> buildList {
            add(getObjCName())
            add(extensionReceiverParameter?.getObjCName())
            valueParameters.forEach { add(it.getObjCName()) }
        }
        else -> listOf(getObjCName())
    }

    private fun List<List<ObjCName?>>.allNamesEquals(): Boolean {
        val first = this[0]
        for (i in 1 until size) {
            if (first != this[i]) return false
        }
        return true
    }
}