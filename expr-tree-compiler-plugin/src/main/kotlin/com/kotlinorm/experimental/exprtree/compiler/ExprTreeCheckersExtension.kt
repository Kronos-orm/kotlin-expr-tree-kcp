package com.kotlinorm.experimental.exprtree.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import com.kotlinorm.experimental.exprtree.api.ExprTree
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.util.concurrent.CopyOnWriteArrayList

class ExprTreeCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(ExprCaptureCallChecker)
    }
}

object ExprCaptureCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callable = (expression.calleeReference as? FirResolvedNamedReference)
            ?.resolvedSymbol as? FirNamedFunctionSymbol
            ?: return
        val isMarkerCall = callable.callableId.asSingleFqName().asString() == MARKER_FQ_NAME
        val capturedLambdas = expression.argumentList.arguments.mapIndexedNotNull { index, argument ->
            val lambda = argument as? FirAnonymousFunctionExpression ?: return@mapIndexedNotNull null
            val parameterIsCaptured = callable.fir.valueParameters
                .getOrNull(index)
                ?.hasAnnotation(EXPR_CAPTURE_ANNOTATION, context.session)
                ?: false
            if (isMarkerCall || parameterIsCaptured) {
                CapturedLambdaSummary(
                    argumentIndex = index,
                    lambdaStartOffset = lambda.source?.startOffset ?: -1,
                    tree = FirExprExtractor.fromLambda(lambda),
                )
            } else {
                null
            }
        }
        if (capturedLambdas.isEmpty()) return

        ExprCaptureRegistry.record(CapturedCallSummary(capturedLambdas))
    }
}

private const val MARKER_FQ_NAME = "com.kotlinorm.experimental.exprtree.api.expr"
private val EXPR_CAPTURE_ANNOTATION = ClassId.topLevel(FqName("com.kotlinorm.experimental.exprtree.api.ExprCapture"))

data class CapturedLambdaSummary(
    val argumentIndex: Int,
    val lambdaStartOffset: Int,
    val tree: ExprTree<Any?, Any?>,
)

data class CapturedCallSummary(
    val lambdas: List<CapturedLambdaSummary>,
)

/** Compiler-session collection is deliberately internal and contains no FIR nodes. */
object ExprCaptureRegistry {
    /** FIR checkers may run concurrently with other analysis work. */
    private val capturedCalls = CopyOnWriteArrayList<CapturedCallSummary>()

    fun record(summary: CapturedCallSummary) {
        capturedCalls += summary
    }

    internal fun clear() = capturedCalls.clear()
    internal fun snapshot(): List<CapturedCallSummary> = capturedCalls.toList()

    internal fun treeForLambdaAt(startOffset: Int): ExprTree<Any?, Any?>? =
        capturedCalls.toList().asReversed().asSequence()
            .flatMap { it.lambdas.asReversed().asSequence() }
            .firstOrNull { it.lambdaStartOffset == startOffset }
            ?.tree
}
