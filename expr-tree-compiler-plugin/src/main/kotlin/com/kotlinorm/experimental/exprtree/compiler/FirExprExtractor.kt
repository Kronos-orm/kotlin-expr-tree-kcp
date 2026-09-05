package com.kotlinorm.experimental.exprtree.compiler

import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.BlockExpr
import com.kotlinorm.experimental.exprtree.api.AssignmentExpr
import com.kotlinorm.experimental.exprtree.api.AssignmentOperator
import com.kotlinorm.experimental.exprtree.api.CallExpr
import com.kotlinorm.experimental.exprtree.api.CallableKind
import com.kotlinorm.experimental.exprtree.api.CallableRef
import com.kotlinorm.experimental.exprtree.api.CaptureDecl
import com.kotlinorm.experimental.exprtree.api.CaptureKind
import com.kotlinorm.experimental.exprtree.api.ConstExpr
import com.kotlinorm.experimental.exprtree.api.DeclId
import com.kotlinorm.experimental.exprtree.api.ElvisExpr
import com.kotlinorm.experimental.exprtree.api.ExprId
import com.kotlinorm.experimental.exprtree.api.ExprNode
import com.kotlinorm.experimental.exprtree.api.ExprTree
import com.kotlinorm.experimental.exprtree.api.LambdaExpr
import com.kotlinorm.experimental.exprtree.api.IfExpr
import com.kotlinorm.experimental.exprtree.api.LocalDecl
import com.kotlinorm.experimental.exprtree.api.LocalDeclarationExpr
import com.kotlinorm.experimental.exprtree.api.Nullability
import com.kotlinorm.experimental.exprtree.api.ParameterDecl
import com.kotlinorm.experimental.exprtree.api.PropertyGetExpr
import com.kotlinorm.experimental.exprtree.api.RefExpr
import com.kotlinorm.experimental.exprtree.api.RefKind
import com.kotlinorm.experimental.exprtree.api.SafeCallExpr
import com.kotlinorm.experimental.exprtree.api.SourceSpan
import com.kotlinorm.experimental.exprtree.api.TreeMetadata
import com.kotlinorm.experimental.exprtree.api.TypeRef
import com.kotlinorm.experimental.exprtree.api.TypeOperator
import com.kotlinorm.experimental.exprtree.api.TypeOperatorExpr
import com.kotlinorm.experimental.exprtree.api.UnaryExpr
import com.kotlinorm.experimental.exprtree.api.UnsupportedExpr
import com.kotlinorm.experimental.exprtree.api.WhenEntryExpr
import com.kotlinorm.experimental.exprtree.api.WhenExpr
import com.kotlinorm.experimental.exprtree.api.WhenSubject
import com.kotlinorm.experimental.exprtree.api.StringTemplateExpr
import com.kotlinorm.experimental.exprtree.api.StringTemplatePart
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirBooleanOperatorExpression
import org.jetbrains.kotlin.fir.expressions.FirComparisonExpression
import org.jetbrains.kotlin.fir.expressions.FirEqualityOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirCheckedSafeCallSubject
import org.jetbrains.kotlin.fir.expressions.FirElvisExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.FirWhenBranch
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirElseIfTrueCondition
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.resolvedType

/**
 * Converts the resolved, supported FIR subset into compiler-independent nodes.
 * IDs are allocated in source traversal order and are only emitted through the
 * generated runtime AST; no FIR object leaks into the public model.
 */
@OptIn(SymbolInternals::class)
internal class FirExprExtractor private constructor(
    private val parameters: Map<FirVariableSymbol<*>, DeclId>,
    private val ids: IdAllocator,
) {
    private val capturesBySymbol = linkedMapOf<Any, CaptureDecl>()
    private val locals = linkedMapOf<FirVariableSymbol<*>, LocalDecl>()
    private var safeCallReceiver: ExprNode? = null

    fun extractTree(lambda: FirAnonymousFunctionExpression): ExprTree<Any?, Any?> {
        val function = lambda.anonymousFunction
        // FIR represents the implicit last expression of a lambda as a return.
        // The public tree represents the lambda result itself, not that compiler
        // control-flow wrapper.
        val body = function.body?.let(::lambdaBody)
            ?: UnsupportedExpr(id(), TypeRef(null), "lambda-without-body", span(lambda))
        return ExprTree(
            parameters = function.valueParameters.map(::parameter),
            captures = capturesBySymbol.values.toList(),
            body = body,
            metadata = TreeMetadata(
                producerVersion = "0.1.0",
                kotlinVersion = "2.4.0",
                attributes = mapOf("producer" to "fir"),
            ),
        )
    }

    private fun lambdaBody(block: FirBlock): ExprNode {
        val expressions = block.statements.mapNotNull(::extractStatement)
        return when (expressions.size) {
            0 -> UnsupportedExpr(id(), typeOf(block), "lambda-empty-body", span(block))
            1 -> expressions.single()
            else -> BlockExpr(id(), typeOf(block), expressions, span(block))
        }
    }

    private fun extractStatement(statement: FirStatement): ExprNode? = when (statement) {
        is FirReturnExpression -> extract(statement.result)
        is FirProperty -> localDeclaration(statement)
        is FirVariableAssignment -> assignment(statement)
        is FirExpression -> extract(statement)
        else -> null
    }

    fun extract(expression: FirExpression): ExprNode = when (expression) {
        is FirLiteralExpression -> ConstExpr(id(), typeOf(expression), expression.value, span(expression))
        is FirBooleanOperatorExpression -> BinaryExpr(
            id = id(),
            type = typeOf(expression),
            operator = CallableRef(
                callableId = "kotlin.Boolean.${expression.kind.name.lowercase()}",
                isOperator = true,
                kind = CallableKind.OPERATOR,
            ),
            left = extract(expression.leftOperand),
            right = extract(expression.rightOperand),
            source = span(expression),
        )
        is FirComparisonExpression -> comparison(expression)
        is FirEqualityOperatorCall -> binaryOperation(
            expression.operation.operator,
            expression.argumentList.arguments,
            expression,
        )
        is FirBlock -> BlockExpr(id(), typeOf(expression), expression.statements.mapNotNull(::extractStatement), span(expression))
        is FirPropertyAccessExpression -> property(expression)
        is FirFunctionCall -> call(expression)
        is FirSafeCallExpression -> safeCall(expression)
        is FirElvisExpression -> ElvisExpr(id(), typeOf(expression), extract(expression.lhs), extract(expression.rhs), span(expression))
        is FirWhenExpression -> whenExpression(expression)
        is FirStringConcatenationCall -> stringTemplate(expression)
        is FirTypeOperatorCall -> typeOperator(expression)
        is FirAnonymousFunctionExpression -> nestedLambda(expression)
        is FirThisReceiverExpression -> thisReceiver(expression)
        is FirCheckedSafeCallSubject -> safeCallReceiver ?: UnsupportedExpr(
            id(), typeOf(expression), "safe-call-subject-without-receiver", span(expression)
        )
        else -> UnsupportedExpr(id(), typeOf(expression), expression::class.simpleName ?: "unknown", span(expression))
    }

    private fun localDeclaration(property: FirProperty): ExprNode {
        val declaration = LocalDecl(
            id = ids.declaration(),
            name = property.name.asString(),
            type = typeOf(property.returnTypeRef),
            mutable = property.isVar,
        )
        locals[property.symbol] = declaration
        return LocalDeclarationExpr(id(), typeOf(property), declaration, property.initializer?.let(::extract), span(property))
    }

    private fun assignment(assignment: FirVariableAssignment): ExprNode = AssignmentExpr(
        id = id(),
        type = typeOf(assignment),
        target = extract(assignment.lValue),
        value = extract(assignment.rValue),
        operator = assignmentOperator(assignment.rValue),
        source = span(assignment),
    )

    private fun whenExpression(expression: FirWhenExpression): ExprNode {
        if (sourceElementTypeName(expression.source) == "IF") {
            val entries = expression.branches.map(::whenEntry)
            if (entries.size !in 1..2) return WhenExpr(id(), typeOf(expression), null, entries, span(expression))
            val first = entries.first()
            val elseBranch = entries.getOrNull(1)?.takeUnless { it.isElse }?.body ?: entries.getOrNull(1)?.body
            return IfExpr(
                id(), typeOf(expression), first.conditions.singleOrNull()
                    ?: UnsupportedExpr(id(), TypeRef(null), "if-without-condition", span(expression)),
                first.body, elseBranch, span(expression),
            )
        }
        val subjectVariable = expression.subjectVariable
        val subject = subjectVariable?.let { variable ->
            val initializer = variable.initializer?.let(::extract)
                ?: return@let null
            val declaration = LocalDecl(
                id = ids.declaration(),
                name = variable.name.asString().takeUnless { it.startsWith("<when-") } ?: "whenSubject",
                type = typeOf(variable.returnTypeRef),
                mutable = variable.isVar,
            )
            val previous = locals.put(variable.symbol, declaration)
            SubjectScope(variable.symbol, WhenSubject(declaration, initializer, span(variable)), previous)
        }
        return try {
            WhenExpr(id(), typeOf(expression), subject?.value, expression.branches.map(::whenEntry), span(expression))
        } finally {
            subject?.let { scoped ->
                if (scoped.previous == null) locals.remove(scoped.symbol) else locals[scoped.symbol] = scoped.previous
            }
        }
    }

    private fun whenEntry(branch: FirWhenBranch): WhenEntryExpr {
        val isElse = branch.condition is FirElseIfTrueCondition
        return WhenEntryExpr(
            id(), typeOf(branch.result),
            conditions = if (isElse) emptyList() else listOf(extract(branch.condition)),
            body = lambdaBody(branch.result),
            isElse = isElse,
            source = span(branch),
        )
    }

    private fun stringTemplate(expression: FirStringConcatenationCall): ExprNode = StringTemplateExpr(
        id(), typeOf(expression),
        expression.argumentList.arguments.map { argument ->
            val value = (argument as? FirLiteralExpression)?.value
            if (value is String) StringTemplatePart.Text(value) else StringTemplatePart.Expression(extract(argument))
        },
        span(expression),
    )

    private fun typeOperator(expression: FirTypeOperatorCall): ExprNode {
        val operand = expression.argumentList.arguments.singleOrNull()
            ?: return UnsupportedExpr(id(), typeOf(expression), "type-operator-without-operand", span(expression))
        val operator = when (expression.operation.name) {
            "IS" -> TypeOperator.IS
            "NOT_IS" -> TypeOperator.IS_NOT
            "AS" -> TypeOperator.AS
            "SAFE_AS" -> TypeOperator.SAFE_AS
            else -> return UnsupportedExpr(id(), typeOf(expression), "unsupported-type-operator:${expression.operation}", span(expression))
        }
        return TypeOperatorExpr(id(), typeOf(expression), operator, extract(operand), typeOf(expression.conversionTypeRef), span(expression))
    }

    private fun safeCall(expression: FirSafeCallExpression): ExprNode {
        val receiver = extract(expression.receiver)
        val oldReceiver = safeCallReceiver
        safeCallReceiver = receiver
        val selector = (expression.selector as? FirExpression)?.let(::extract)
            ?: UnsupportedExpr(id(), typeOf(expression), "safe-call-non-expression-selector", span(expression))
        safeCallReceiver = oldReceiver
        return SafeCallExpr(id(), typeOf(expression), receiver, selector, span(expression))
    }

    private fun comparison(expression: FirComparisonExpression): ExprNode {
        val compare = expression.compareToCall
        val receiver = compare.dispatchReceiver ?: compare.extensionReceiver ?: compare.explicitReceiver
        val argument = compare.argumentList.arguments.singleOrNull()
        if (receiver == null || argument == null) {
            return UnsupportedExpr(id(), typeOf(expression), "comparison-without-operands", span(expression))
        }
        return BinaryExpr(
            id = id(),
            type = typeOf(expression),
            operator = CallableRef(
                callableId = "kotlin.operator.${expression.operation.operator}",
                isOperator = true,
                kind = CallableKind.OPERATOR,
            ),
            left = extract(receiver),
            right = extract(argument),
            source = span(expression),
        )
    }

    private fun binaryOperation(
        operator: String,
        operands: List<FirExpression>,
        expression: FirExpression,
    ): ExprNode {
        if (operands.size != 2) {
            return UnsupportedExpr(id(), typeOf(expression), "binary-operation-without-two-operands", span(expression))
        }
        return BinaryExpr(
            id = id(),
            type = typeOf(expression),
            operator = CallableRef("kotlin.operator.$operator", isOperator = true, kind = CallableKind.OPERATOR),
            left = extract(operands[0]),
            right = extract(operands[1]),
            source = span(expression),
        )
    }

    private fun nestedLambda(expression: FirAnonymousFunctionExpression): ExprNode {
        val function = expression.anonymousFunction
        val nestedParameters = function.valueParameters.associate { value ->
            value.symbol as FirVariableSymbol<*> to ids.declaration()
        }
        val nested = FirExprExtractor(nestedParameters, ids).extractTree(expression)
        return LambdaExpr(id(), typeOf(expression), nested.parameters, nested.captures, nested.body, span(expression))
    }

    private fun property(expression: FirPropertyAccessExpression): ExprNode {
        val symbol = (expression.calleeReference as? FirResolvedNamedReference)?.resolvedSymbol
        val receiver = expression.explicitReceiver ?: expression.extensionReceiver ?: expression.dispatchReceiver
        val isSafeCallSelector = receiver is FirCheckedSafeCallSubject
        // Object and class qualifiers are compile-time receivers. The callable id
        // identifies their target without manufacturing an unsupported runtime node.
        val receiverNode = receiver?.takeUnless {
            it is FirCheckedSafeCallSubject || it is FirResolvedQualifier
        }?.let(::extract)
        if (symbol is FirVariableSymbol<*> && receiverNode == null && !isSafeCallSelector) {
            val declaration = parameters[symbol]
            return if (declaration != null) {
                RefExpr(id(), typeOf(expression), declaration, expression.calleeReference.name.asString(), RefKind.PARAMETER, span(expression))
            } else {
                val local = locals[symbol]
                if (local != null) {
                    RefExpr(id(), typeOf(expression), local.id, local.name, RefKind.LOCAL, span(expression))
                } else if (symbol is FirPropertySymbol && !symbol.fir.isLocal) {
                    // Top-level properties and static object properties are not
                    // closure values. Preserve the resolved property reference so
                    // consumers can decide whether and when to evaluate it.
                    PropertyGetExpr(
                        id(), typeOf(expression), callable(symbol, expression.calleeReference.name.asString()), null, span(expression)
                    )
                } else {
                    val capture = capture(symbol, expression.calleeReference.name.asString(), typeOf(expression))
                    RefExpr(id(), typeOf(expression), capture.id, capture.name, RefKind.CAPTURE, span(expression))
                }
            }
        }
        return PropertyGetExpr(
            id(), typeOf(expression), callable(symbol, expression.calleeReference.name.asString()), receiverNode, span(expression)
        )
    }

    private fun thisReceiver(expression: FirThisReceiverExpression): ExprNode {
        val name = expression.calleeReference.labelName.orEmpty().ifBlank { "this" }
        val boundSymbol = expression.calleeReference.boundSymbol ?: return UnsupportedExpr(
            id(), typeOf(expression), "this-without-bound-symbol", span(expression)
        )
        val capture = capturesBySymbol.getOrPut(boundSymbol) {
            CaptureDecl(
                id = ids.declaration(),
                name = name,
                type = typeOf(expression),
                captureKind = CaptureKind.THIS,
            )
        }
        return RefExpr(id(), typeOf(expression), capture.id, capture.name, RefKind.THIS, span(expression))
    }

    private fun call(expression: FirFunctionCall): ExprNode {
        val symbol = (expression.calleeReference as? FirResolvedNamedReference)?.resolvedSymbol
        val dispatch = expression.dispatchReceiver?.takeUnless { it is FirCheckedSafeCallSubject }?.let(::extract)
        val extension = expression.extensionReceiver?.takeUnless { it is FirCheckedSafeCallSubject }?.let(::extract)
        val receiver = dispatch ?: extension ?: expression.explicitReceiver?.takeUnless { it is FirCheckedSafeCallSubject }?.let(::extract)
        val args = expression.argumentList.arguments.map { extract(it) }
        val name = expression.calleeReference.name.asString()
        val callable = callable(symbol, name)
        if (receiver != null && args.size == 1 && name in BINARY_OPERATOR_NAMES) {
            return BinaryExpr(id(), typeOf(expression), callable, receiver, args.single(), span(expression))
        }
        if (receiver != null && args.isEmpty() && name in UNARY_OPERATOR_NAMES) {
            return UnaryExpr(id(), typeOf(expression), callable, receiver, span(expression))
        }
        return CallExpr(id(), typeOf(expression), callable, dispatch ?: receiver, extension, args, span(expression))
    }

    private fun parameter(parameter: FirValueParameter): ParameterDecl {
        val declaration = parameters[parameter.symbol as FirVariableSymbol<*>]
            ?: error("Missing declaration id for lambda parameter ${parameter.name}")
        return ParameterDecl(
            id = declaration,
            name = parameter.name.asString(),
            type = typeOf(parameter.returnTypeRef),
            isVararg = parameter.isVararg,
            isCrossinline = parameter.isCrossinline,
            isNoinline = parameter.isNoinline,
        )
    }

    private fun capture(symbol: Any, name: String, type: TypeRef): CaptureDecl =
        capturesBySymbol.getOrPut(symbol) {
            val variable = symbol as? FirVariableSymbol<*>
            CaptureDecl(
                id = ids.declaration(),
                name = name,
                type = type,
                captureKind = if (variable?.isVar == true) CaptureKind.MUTABLE_CELL else CaptureKind.VALUE,
                mutable = variable?.isVar == true,
            )
        }

    private fun callable(symbol: Any?, fallback: String): CallableRef {
        val callableSymbol = symbol as? FirCallableSymbol<*>
        val callableId = callableSymbol?.callableId?.asSingleFqName()?.asString() ?: fallback
        val operator = fallback in BINARY_OPERATOR_NAMES || fallback in UNARY_OPERATOR_NAMES
        return CallableRef(
            callableId = callableId,
            isOperator = operator,
            kind = when {
                operator -> CallableKind.OPERATOR
                symbol is FirPropertySymbol -> CallableKind.PROPERTY
                symbol is FirNamedFunctionSymbol -> CallableKind.FUNCTION
                else -> CallableKind.UNKNOWN
            },
            receiverType = callableSymbol?.resolvedReceiverType?.let(::typeOf),
        )
    }

    private fun id(): ExprId = ids.expression()

    private fun typeOf(element: FirElement): TypeRef = (element as? FirExpression)
        ?.let { typeOf(it.resolvedType) }
        ?: TypeRef(null)

    private fun typeOf(typeRef: FirTypeRef): TypeRef = typeOf((typeRef as? FirResolvedTypeRef)?.coneType)

    private fun typeOf(type: ConeKotlinType?): TypeRef {
        if (type == null) return TypeRef(null)
        val classLike = type as? ConeClassLikeType
        return TypeRef(
            classifierId = classLike?.lookupTag?.classId?.asSingleFqName()?.asString() ?: type.toString().removeSuffix("?"),
            nullability = if (type.isMarkedNullable) Nullability.NULLABLE else Nullability.NON_NULL,
            arguments = type.typeArguments.mapNotNull { (it as? ConeKotlinTypeProjection)?.type?.let(::typeOf) },
        )
    }

    private fun span(element: FirElement): SourceSpan? = spanFrom(
        when (element) {
            is FirStatement -> element.source
            is FirDeclaration -> element.source
            is FirWhenBranch -> element.source
            else -> null
        }
    )

    /** Keeps source metadata without exposing compiler PSI classes in the runtime model. */
    private fun spanFrom(source: Any?): SourceSpan? {
        source ?: return null
        val startOffset = invokeInt(source, "getStartOffset") ?: return null
        val endOffset = invokeInt(source, "getEndOffset") ?: return null
        val psi = invoke(source, "getPsi")
        val containingFile = psi?.let { invoke(it, "getContainingFile") }
        val virtualFile = containingFile?.let { invoke(it, "getVirtualFile") }
        val fileId = (virtualFile?.let { invokeString(it, "getPath") }
            ?: containingFile?.let { invokeString(it, "getName") }
            ?: "<unknown>")
        return SourceSpan(fileId, startOffset, endOffset)
    }

    /** Reads compiler source metadata without linking against a particular IntelliJ PSI ABI. */
    private fun sourceElementTypeName(source: Any?): String? = runCatching {
        source?.javaClass?.methods
            ?.firstOrNull { it.name == "getElementType" && it.parameterCount == 0 }
            ?.invoke(source)
            ?.toString()
    }.getOrNull()

    private fun assignmentOperator(rValue: FirExpression): AssignmentOperator {
        val operationName = (rValue as? FirFunctionCall)?.calleeReference?.name?.asString()
        return when (operationName) {
            "plus" -> AssignmentOperator.PLUS_ASSIGN
            "minus" -> AssignmentOperator.MINUS_ASSIGN
            "times" -> AssignmentOperator.TIMES_ASSIGN
            "div" -> AssignmentOperator.DIV_ASSIGN
            "rem" -> AssignmentOperator.REM_ASSIGN
            else -> AssignmentOperator.SET
        }
    }

    private fun invoke(target: Any, methodName: String): Any? = runCatching {
        target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }?.invoke(target)
    }.getOrNull()

    private fun invokeInt(target: Any, methodName: String): Int? = invoke(target, methodName) as? Int

    private fun invokeString(target: Any, methodName: String): String? = invoke(target, methodName) as? String

    private class IdAllocator(parameterCount: Int) {
        private var nextExpression = 1L
        private var nextDeclaration = parameterCount.toLong() + 1L

        fun expression(): ExprId = ExprId(nextExpression++)
        fun declaration(): DeclId = DeclId(nextDeclaration++)
    }

    private data class SubjectScope(
        val symbol: FirVariableSymbol<*>,
        val value: WhenSubject,
        val previous: LocalDecl?,
    )

    companion object {
        private val BINARY_OPERATOR_NAMES = setOf(
            "plus", "minus", "times", "div", "rem", "compareTo", "equals", "and", "or", "xor",
            "rangeTo", "contains",
        )
        private val UNARY_OPERATOR_NAMES = setOf("unaryPlus", "unaryMinus", "not", "inc", "dec")

        fun fromLambda(lambda: FirAnonymousFunctionExpression): ExprTree<Any?, Any?> {
            val values = lambda.anonymousFunction.valueParameters
            val parameterIds = values.mapIndexed { index, parameter ->
                parameter.symbol as FirVariableSymbol<*> to DeclId(index.toLong() + 1)
            }.toMap()
            return FirExprExtractor(parameterIds, IdAllocator(values.size)).extractTree(lambda)
        }
    }
}
