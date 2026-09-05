package com.kotlinorm.experimental.exprtree.api

@JvmInline
value class ExprId(val value: Long)

@JvmInline
value class DeclId(val value: Long)

enum class Nullability {
    NON_NULL,
    NULLABLE,
    UNKNOWN,
}

data class TypeRef(
    val classifierId: String?,
    val nullability: Nullability = Nullability.UNKNOWN,
    val arguments: List<TypeRef> = emptyList(),
    val abbreviatedType: TypeRef? = null,
    val expandedTypeId: String? = null,
    val flags: Set<TypeFlag> = emptySet(),
)

enum class TypeFlag { INLINE_CLASS, VALUE_CLASS, TYPE_ALIAS, SUSPEND, DEFINITELY_NON_NULL }

data class SourceSpan(
    val fileId: String,
    val startOffset: Int,
    val endOffset: Int,
)

data class CallableRef(
    val callableId: String,
    val isOperator: Boolean = false,
    val kind: CallableKind = CallableKind.FUNCTION,
    val jvmSignature: String? = null,
    val receiverType: TypeRef? = null,
    val valueParameters: List<ValueParameterRef> = emptyList(),
    val isFakeOverride: Boolean = false,
    val overriddenCallableIds: List<String> = emptyList(),
) {
    val name: String
        get() = callableId.substringAfterLast('.')
}

enum class CallableKind { FUNCTION, PROPERTY, GETTER, SETTER, CONSTRUCTOR, OPERATOR, CLASSIFIER, UNKNOWN }

data class ValueParameterRef(
    val name: String,
    val type: TypeRef,
    val index: Int,
    val hasDefault: Boolean = false,
    val isVararg: Boolean = false,
)

/**
 * Closed algebraic data type for every expression form the runtime exposes.
 * Consumers can exhaustively branch on its data-class variants without
 * inheriting an open extension point that would weaken serialization and
 * visitor invariants.
 */
sealed interface ExprNode {
    val id: ExprId
    val type: TypeRef
    val source: SourceSpan?
    val origin: OriginRef?
}

data class ConstExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val value: Any?,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class RefExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val declaration: DeclId,
    val name: String,
    val kind: RefKind,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

enum class RefKind { PARAMETER, LOCAL, CAPTURE, THIS, SUPER }

data class PropertyGetExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val property: CallableRef,
    val receiver: ExprNode?,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class CallExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val callable: CallableRef,
    val dispatchReceiver: ExprNode?,
    val extensionReceiver: ExprNode?,
    val arguments: List<ExprNode>,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class UnaryExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val operator: CallableRef,
    val operand: ExprNode,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class BinaryExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val operator: CallableRef,
    val left: ExprNode,
    val right: ExprNode,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class SafeCallExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val receiver: ExprNode,
    val selector: ExprNode,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class ElvisExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val left: ExprNode,
    val right: ExprNode,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class BlockExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val statements: List<ExprNode>,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class LambdaExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val parameters: List<ParameterDecl>,
    val captures: List<CaptureDecl>,
    val body: ExprNode?,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A local `val`/`var` declaration, represented as an expression statement. */
data class LocalDeclarationExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val declaration: LocalDecl,
    val initializer: ExprNode? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** An assignment expression, including compound assignment operators. */
data class AssignmentExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val target: ExprNode,
    val value: ExprNode,
    val operator: AssignmentOperator = AssignmentOperator.SET,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

enum class AssignmentOperator {
    SET,
    PLUS_ASSIGN,
    MINUS_ASSIGN,
    TIMES_ASSIGN,
    DIV_ASSIGN,
    REM_ASSIGN,
}

/** Kotlin's expression-valued `if` form. */
data class IfExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val condition: ExprNode,
    val thenBranch: ExprNode,
    val elseBranch: ExprNode? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A single branch in a [WhenExpr]. Conditions are empty only for an `else` branch. */
data class WhenEntryExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val conditions: List<ExprNode>,
    val body: ExprNode,
    val isElse: Boolean = false,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/**
 * The single-evaluation binding introduced by a subject-style Kotlin `when`.
 *
 * For `when (source) { ... }`, [initializer] is evaluated once and branch
 * conditions refer to [declaration]. This also models explicit subject
 * declarations such as `when (val name = source) { ... }` without exposing a
 * compiler-specific synthetic symbol to consumers.
 */
data class WhenSubject(
    val declaration: LocalDecl,
    val initializer: ExprNode,
    val source: SourceSpan? = null,
)

/** Kotlin's expression-valued `when` form. */
data class WhenExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val subject: WhenSubject?,
    val entries: List<WhenEntryExpr>,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A string template, retaining literal text and interpolated expressions separately. */
data class StringTemplateExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val parts: List<StringTemplatePart>,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

sealed interface StringTemplatePart {
    data class Text(val text: String) : StringTemplatePart
    data class Expression(val expression: ExprNode) : StringTemplatePart
}

/** Kotlin type checks and casts (`is`, `!is`, `as`, `as?`). */
data class TypeOperatorExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val operator: TypeOperator,
    val operand: ExprNode,
    val targetType: TypeRef,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

enum class TypeOperator { IS, IS_NOT, AS, SAFE_AS }

data class UnsupportedExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val reason: String,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class ParameterDecl(val id: DeclId, val name: String, val type: TypeRef, val isVararg: Boolean = false, val isCrossinline: Boolean = false, val isNoinline: Boolean = false)

data class LocalDecl(val id: DeclId, val name: String, val type: TypeRef, val mutable: Boolean = false)

data class CaptureDecl(val id: DeclId, val name: String, val type: TypeRef, val captureKind: CaptureKind = CaptureKind.VALUE, val mutable: Boolean = false)

enum class CaptureKind { VALUE, MUTABLE_CELL, THIS, CONTEXT_RECEIVER }

data class ExprTree<P, R>(
    val schemaVersion: Int = 1,
    val parameters: List<ParameterDecl>,
    val captures: List<CaptureDecl>,
    val body: ExprNode,
    val diagnostics: List<TreeDiagnostic> = emptyList(),
    val metadata: TreeMetadata = TreeMetadata(),
)
