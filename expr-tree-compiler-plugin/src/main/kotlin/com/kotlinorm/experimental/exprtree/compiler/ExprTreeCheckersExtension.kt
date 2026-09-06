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
import java.util.concurrent.ConcurrentHashMap

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
                    sourceFile = context.containingFile?.path ?: context.containingFile?.name.orEmpty(),
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
    val sourceFile: String,
    val lambdaStartOffset: Int,
    val tree: ExprTree<Any?, Any?>,
)

data class CapturedCallSummary(
    val lambdas: List<CapturedLambdaSummary>,
)

/**
 * Bridge data from FIR checking to IR generation without retaining compiler nodes.
 * A lambda is consumed once by its source file and offset, so separate compiler
 * modules cannot accidentally reuse a stale tree with the same offset.
 */
object ExprCaptureRegistry {
    private val trees = ConcurrentHashMap<CaptureKey, ExprTree<Any?, Any?>>()

    fun record(summary: CapturedCallSummary) {
        summary.lambdas.forEach { lambda ->
            trees[key(lambda.sourceFile, lambda.lambdaStartOffset)] = lambda.tree
        }
    }

    internal fun clear() = trees.clear()
    internal fun snapshot(): List<ExprTree<Any?, Any?>> = trees.values.toList()

    internal fun treeForLambdaAt(sourceFile: String, startOffset: Int): ExprTree<Any?, Any?>? =
        trees[key(sourceFile, startOffset)]

    internal fun takeTreeForLambdaAt(sourceFile: String, startOffset: Int): ExprTree<Any?, Any?>? =
        trees.remove(key(sourceFile, startOffset)) ?: run {
            // FIR and IR can spell the same source path differently in compiler tests.
            // Match the filename only when that source location is unambiguous.
            val sourceName = sourceFile.replace('\\', '/').substringAfterLast('/')
            val candidates = trees.entries.filter { candidate ->
                candidate.key.startOffset == startOffset &&
                    candidate.key.sourceFile.substringAfterLast('/') == sourceName
            }
            if (candidates.size == 1) trees.remove(candidates.single().key) else null
        }

    private fun key(sourceFile: String, startOffset: Int): CaptureKey = CaptureKey(
        sourceFile.replace('\\', '/'),
        startOffset,
    )

    private data class CaptureKey(val sourceFile: String, val startOffset: Int)
}
