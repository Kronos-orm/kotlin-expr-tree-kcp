package com.kotlinorm.experimental.exprtree.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import com.kotlinorm.experimental.exprtree.api.ExprTree

class ExprTreeCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(ExprCaptureCallChecker)
    }
}

object ExprCaptureCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callable = (expression.calleeReference as? FirResolvedNamedReference)
            ?.resolvedSymbol as? FirNamedFunctionSymbol
            ?: return
        if (callable.callableId.asSingleFqName().asString() != "com.kotlinorm.experimental.exprtree.api.expr") return

        val trees = expression.argumentList.arguments
            .filterIsInstance<FirAnonymousFunctionExpression>()
            .map(FirExprExtractor::fromLambda)
        ExprCaptureRegistry.record(CapturedCallSummary(
            callableId = callable.callableId.asSingleFqName().asString(),
            argumentCount = expression.argumentList.arguments.size,
            callStartOffset = expression.source?.startOffset ?: -1,
            trees = trees,
        ))
    }
}

data class CapturedCallSummary(
    val callableId: String,
    val argumentCount: Int,
    val callStartOffset: Int,
    val trees: List<ExprTree<Any?, Any?>>,
)

/** Compiler-session collection is deliberately internal and contains no FIR nodes. */
object ExprCaptureRegistry {
    private val capturedCalls = mutableListOf<CapturedCallSummary>()

    fun record(summary: CapturedCallSummary) {
        capturedCalls += summary
    }

    internal fun clear() = capturedCalls.clear()
    internal fun snapshot(): List<CapturedCallSummary> = capturedCalls.toList()

    internal fun treeAt(startOffset: Int): ExprTree<Any?, Any?>? =
        capturedCalls.lastOrNull { it.callStartOffset == startOffset }?.trees?.singleOrNull()
}
