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
    /** Source-level token such as `+` or `<` when this callable came from operator syntax. */
    val operatorToken: String? = null,
    /** Parameters supplied through Kotlin context-parameter syntax. */
    val contextParameters: List<ValueParameterRef> = emptyList(),
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
    /** Declaration id for ordinary references; receiver references may not have one. */
    val declaration: DeclId?,
    val name: String,
    val kind: RefKind,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
    /** Source label for qualified receivers such as `this@Outer` or `super@Outer`. */
    val label: String? = null,
    /** Explicit supertype selected by `super<Type>`; ordinary references leave this null. */
    val qualifierType: TypeRef? = null,
) : ExprNode

enum class RefKind { PARAMETER, LOCAL, CAPTURE, THIS, SUPER }

data class PropertyAccessExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val property: CallableRef,
    val receiver: ExprNode?,
    val operation: PropertyAccessOperation = PropertyAccessOperation.GET,
    val value: ExprNode? = null,
    val assignmentOperator: AssignmentOperator = AssignmentOperator.SET,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

enum class PropertyAccessOperation { GET, SET }

data class CallExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val callable: CallableRef,
    val dispatchReceiver: ExprNode?,
    val extensionReceiver: ExprNode?,
    val arguments: List<ExprNode>,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
    /** Arguments bound to the callable's context parameters. */
    val contextArguments: List<ExprNode> = emptyList(),
    /** FIR component index for positional destructuring calls. */
    val componentIndex: Int? = null,
) : ExprNode

/** Indexed read such as `receiver[index]` or `receiver[i, j]`. */
data class IndexAccessExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val callable: CallableRef,
    val receiver: ExprNode,
    val indices: List<ExprNode>,
    val operation: IndexAccessOperation = IndexAccessOperation.GET,
    val value: ExprNode? = null,
    val assignmentOperator: AssignmentOperator = AssignmentOperator.SET,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

enum class IndexAccessOperation { GET, SET }

/** Property write, retaining the resolved setter and assignment operation. */

enum class DestructuringMode { POSITIONAL, NAME_BASED }

/** A single binding in a FIR destructuring block, kept in source order. */
data class DestructuringBinding(
    val declaration: LocalDecl,
    val initializer: ExprNode,
    val componentIndex: Int? = null,
    val propertyName: String? = null,
    val source: SourceSpan? = null,
)

/** A positional or name-based destructuring declaration lowered by FIR. */
data class DestructuringExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val initializer: ExprNode,
    val entries: List<DestructuringBinding>,
    val mode: DestructuringMode,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class IncDecExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val operand: ExprNode,
    val operation: IncDecOperation,
    val operator: CallableRef,
    val prefix: Boolean,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

enum class IncDecOperation { INC, DEC }

data class CallableReferenceExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val callable: CallableRef,
    val receiver: ExprNode? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

data class SmartCastExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val expression: ExprNode,
    val smartCastType: TypeRef,
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

data class RangeExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val start: ExprNode,
    val end: ExprNode,
    val operation: RangeOperation,
    val callable: CallableRef,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

enum class RangeOperation { RANGE_TO, RANGE_UNTIL, UNTIL, DOWN_TO }

/** An explicit Kotlin return, preserving its optional label and value. */
data class ReturnExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val value: ExprNode,
    val targetId: ExprId? = null,
    val targetLabel: String? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A loop with an optional source label. */
data class WhileExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val condition: ExprNode,
    val body: ExprNode,
    val targetId: ExprId,
    val label: String? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A post-test loop with an optional source label. */
data class DoWhileExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val body: ExprNode,
    val condition: ExprNode,
    val targetId: ExprId,
    val label: String? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A break transfer targeting the nearest or named loop. */
data class BreakExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val targetId: ExprId? = null,
    val targetLabel: String? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A continue transfer targeting the nearest or named loop. */
data class ContinueExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val targetId: ExprId? = null,
    val targetLabel: String? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A Kotlin `for (element in iterable)` loop. */
data class ForLoopExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val declaration: LocalDecl,
    val iterable: ExprNode,
    val body: ExprNode,
    val targetId: ExprId,
    val label: String? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A Kotlin `throw` transfer expression. */
data class ThrowExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val value: ExprNode,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A Kotlin `try` expression with zero or more catches and an optional `finally` block. */
data class TryExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val tryBlock: ExprNode,
    val catches: List<CatchExpr>,
    val finallyBlock: ExprNode? = null,
    override val source: SourceSpan? = null,
    override val origin: OriginRef? = null,
) : ExprNode

/** A `catch` clause within a [TryExpr]. */
data class CatchExpr(
    override val id: ExprId,
    override val type: TypeRef,
    val parameter: LocalDecl,
    val body: ExprNode,
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
    /** Original Kotlin source for runtime-side recovery or a secondary parser. */
    val sourceText: String? = null,
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
