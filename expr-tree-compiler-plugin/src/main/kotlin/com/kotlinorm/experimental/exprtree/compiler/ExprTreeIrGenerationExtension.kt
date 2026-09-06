package com.kotlinorm.experimental.exprtree.compiler

import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.BlockExpr
import com.kotlinorm.experimental.exprtree.api.AssignmentExpr
import com.kotlinorm.experimental.exprtree.api.CallExpr
import com.kotlinorm.experimental.exprtree.api.CatchExpr
import com.kotlinorm.experimental.exprtree.api.CallableKind
import com.kotlinorm.experimental.exprtree.api.ConstExpr
import com.kotlinorm.experimental.exprtree.api.ElvisExpr
import com.kotlinorm.experimental.exprtree.api.ExprNode
import com.kotlinorm.experimental.exprtree.api.ExprTree
import com.kotlinorm.experimental.exprtree.api.IfExpr
import com.kotlinorm.experimental.exprtree.api.LocalDeclarationExpr
import com.kotlinorm.experimental.exprtree.api.Nullability
import com.kotlinorm.experimental.exprtree.api.PropertyGetExpr
import com.kotlinorm.experimental.exprtree.api.RefExpr
import com.kotlinorm.experimental.exprtree.api.RefKind
import com.kotlinorm.experimental.exprtree.api.SafeCallExpr
import com.kotlinorm.experimental.exprtree.api.StringTemplateExpr
import com.kotlinorm.experimental.exprtree.api.StringTemplatePart
import com.kotlinorm.experimental.exprtree.api.TypeOperatorExpr
import com.kotlinorm.experimental.exprtree.api.TryExpr
import com.kotlinorm.experimental.exprtree.api.UnaryExpr
import com.kotlinorm.experimental.exprtree.api.UnsupportedExpr
import com.kotlinorm.experimental.exprtree.api.WhenEntryExpr
import com.kotlinorm.experimental.exprtree.api.WhenExpr
import com.kotlinorm.experimental.exprtree.api.WhenSubject
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getValueArgument
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.deepCopyWithoutPatchingParents
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.ClassId

/** Emits runtime tree carriers for marker calls and annotated lambda parameters. */
internal class ExprTreeIrGenerationExtension : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transform(object : IrElementTransformerVoid() {
            private var currentFile: IrFile? = null

            override fun visitFile(declaration: IrFile): IrFile {
                val previousFile = currentFile
                currentFile = declaration
                declaration.transformChildrenVoid()
                currentFile = previousFile
                return declaration
            }

            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid()
                val builder = pluginContext.irBuiltIns.createIrBuilder(expression.symbol, expression.startOffset, expression.endOffset)
                val emitter = ExprTreeIrEmitter(pluginContext, builder, currentFile)
                if (expression.symbol.owner.fqNameWhenAvailable?.asString() == MARKER_FQ_NAME) {
                    val lambda = expression.arguments.getOrNull(0) as? IrFunctionExpression ?: return expression
                    val tree = ExprCaptureRegistry.takeTreeForLambdaAt(currentFile?.fileEntry?.name.orEmpty(), lambda.startOffset)
                        ?: return expression
                    return emitter.captured(tree, captureValues(lambda))
                }
                expression.arguments.forEachIndexed { index, argument ->
                    val lambda = argument as? IrFunctionExpression
                        ?: return@forEachIndexed
                    val tree = ExprCaptureRegistry.takeTreeForLambdaAt(currentFile?.fileEntry?.name.orEmpty(), lambda.startOffset)
                        ?: return@forEachIndexed
                    expression.arguments[index] = emitter.capturedLambda(
                        lambda,
                        tree,
                        captureValues(lambda),
                    )
                }
                return expression
            }
        }, null)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun captureValues(lambda: IrFunctionExpression): List<IrExpression> {
        val ownParameters = lambda.function.parameters.map { it.symbol }.toSet()
        val ownLocals = linkedSetOf<org.jetbrains.kotlin.ir.symbols.IrValueSymbol>()
        val values = linkedMapOf<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>()
        lambda.function.body?.acceptChildrenVoid(object : org.jetbrains.kotlin.ir.visitors.IrVisitorVoid() {
            override fun visitElement(element: org.jetbrains.kotlin.ir.IrElement) { element.acceptChildrenVoid(this) }
            override fun visitVariable(declaration: org.jetbrains.kotlin.ir.declarations.IrVariable) {
                ownLocals += declaration.symbol
                declaration.acceptChildrenVoid(this)
            }
            override fun visitFunctionExpression(expression: IrFunctionExpression) {
                // Nested lambdas own their locals and are represented independently by FIR.
            }
            override fun visitGetValue(expression: IrGetValue) {
                val owner = expression.symbol.owner
                val captureCandidate = owner is IrValueParameter ||
                    (owner is IrVariable && owner.origin == IrDeclarationOrigin.DEFINED)
                if (!ownParameters.contains(expression.symbol) && !ownLocals.contains(expression.symbol) && captureCandidate) {
                    values.putIfAbsent(expression.symbol, expression.deepCopyWithoutPatchingParents())
                }
            }
        })
        return values.values.toList()
    }
}

private const val MARKER_FQ_NAME = "com.kotlinorm.experimental.exprtree.api.expr"

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class ExprTreeIrEmitter(
    private val context: IrPluginContext,
    private val builder: IrBuilderWithScope,
    sourceFile: IrFile?,
) {
    private val anyN = context.irBuiltIns.anyNType
    private val sourceFinder = sourceFile?.let(context::finderForSource) ?: context.finderForBuiltins()
    private fun klass(name: String): IrClassSymbol = requireNotNull(
        sourceFinder.findClass(classId(name))
    ) { "Missing runtime class $name" }

    private fun classId(name: String): ClassId {
        val apiPrefix = "com.kotlinorm.experimental.exprtree.api."
        if (!name.startsWith(apiPrefix)) return ClassId.topLevel(FqName(name))
        return ClassId(FqName(apiPrefix.removeSuffix(".")), FqName(name.removePrefix(apiPrefix)), false)
    }

    fun captured(tree: ExprTree<Any?, Any?>, values: List<IrExpression>): IrExpression = new(
        "com.kotlinorm.experimental.exprtree.api.CapturedExpr",
        treeExpr(tree),
        builder.irVararg(anyN, values),
        builder.irInt(1),
    )

    fun capturedLambda(
        lambda: IrFunctionExpression,
        tree: ExprTree<Any?, Any?>,
        values: List<IrExpression>,
    ): IrExpression = new(
        "com.kotlinorm.experimental.exprtree.api.CapturedLambda",
        lambda,
        captured(tree, values),
    )

    private fun treeExpr(tree: ExprTree<Any?, Any?>): IrExpression = new(
        "com.kotlinorm.experimental.exprtree.api.ExprTree",
        builder.irInt(tree.schemaVersion), list("com.kotlinorm.experimental.exprtree.api.ParameterDecl", tree.parameters.map(::parameter)),
        list("com.kotlinorm.experimental.exprtree.api.CaptureDecl", tree.captures.map(::capture)), node(tree.body),
        list(anyN, emptyList()), metadata(tree.metadata.producerVersion, tree.metadata.kotlinVersion, tree.metadata.targetPlatform),
    )

    private fun parameter(value: com.kotlinorm.experimental.exprtree.api.ParameterDecl) = new(
        "com.kotlinorm.experimental.exprtree.api.ParameterDecl", decl(value.id.value), builder.irString(value.name), type(value.type),
        builder.irBoolean(value.isVararg), builder.irBoolean(value.isCrossinline), builder.irBoolean(value.isNoinline),
    )

    private fun capture(value: com.kotlinorm.experimental.exprtree.api.CaptureDecl) = new(
        "com.kotlinorm.experimental.exprtree.api.CaptureDecl", decl(value.id.value), builder.irString(value.name), type(value.type),
        enum("com.kotlinorm.experimental.exprtree.api.CaptureKind", value.captureKind.name), builder.irBoolean(value.mutable),
    )

    private fun local(value: com.kotlinorm.experimental.exprtree.api.LocalDecl) = new(
        "com.kotlinorm.experimental.exprtree.api.LocalDecl", decl(value.id.value), builder.irString(value.name), type(value.type), builder.irBoolean(value.mutable),
    )

    private fun whenSubject(value: WhenSubject) = new(
        "com.kotlinorm.experimental.exprtree.api.WhenSubject",
        local(value.declaration), node(value.initializer), source(value.source),
    )

    private fun stringPart(value: StringTemplatePart): IrExpression = when (value) {
        is StringTemplatePart.Text -> new("com.kotlinorm.experimental.exprtree.api.StringTemplatePart.Text", builder.irString(value.text))
        is StringTemplatePart.Expression -> new("com.kotlinorm.experimental.exprtree.api.StringTemplatePart.Expression", node(value.expression))
    }

    private fun source(value: com.kotlinorm.experimental.exprtree.api.SourceSpan?): IrExpression = value?.let {
        new("com.kotlinorm.experimental.exprtree.api.SourceSpan", builder.irString(it.fileId), builder.irInt(it.startOffset), builder.irInt(it.endOffset))
    } ?: nullOf("com.kotlinorm.experimental.exprtree.api.SourceSpan")

    private fun node(value: ExprNode): IrExpression = when (value) {
        is ConstExpr -> new("com.kotlinorm.experimental.exprtree.api.ConstExpr", exprId(value.id.value), type(value.type), constant(value.value), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is RefExpr -> new("com.kotlinorm.experimental.exprtree.api.RefExpr", exprId(value.id.value), type(value.type), decl(value.declaration.value), builder.irString(value.name), enum("com.kotlinorm.experimental.exprtree.api.RefKind", value.kind.name), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is PropertyGetExpr -> new("com.kotlinorm.experimental.exprtree.api.PropertyGetExpr", exprId(value.id.value), type(value.type), callable(value.property.callableId, value.property.isOperator, value.property.kind), value.receiver?.let(::node) ?: nullOf("com.kotlinorm.experimental.exprtree.api.ExprNode"), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is CallExpr -> new("com.kotlinorm.experimental.exprtree.api.CallExpr", exprId(value.id.value), type(value.type), callable(value.callable.callableId, value.callable.isOperator, value.callable.kind), value.dispatchReceiver?.let(::node) ?: nullOf("com.kotlinorm.experimental.exprtree.api.ExprNode"), value.extensionReceiver?.let(::node) ?: nullOf("com.kotlinorm.experimental.exprtree.api.ExprNode"), list("com.kotlinorm.experimental.exprtree.api.ExprNode", value.arguments.map(::node)), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is UnaryExpr -> new("com.kotlinorm.experimental.exprtree.api.UnaryExpr", exprId(value.id.value), type(value.type), callable(value.operator.callableId, value.operator.isOperator, value.operator.kind), node(value.operand), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is BinaryExpr -> new("com.kotlinorm.experimental.exprtree.api.BinaryExpr", exprId(value.id.value), type(value.type), callable(value.operator.callableId, value.operator.isOperator, value.operator.kind), node(value.left), node(value.right), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is SafeCallExpr -> new("com.kotlinorm.experimental.exprtree.api.SafeCallExpr", exprId(value.id.value), type(value.type), node(value.receiver), node(value.selector), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is ElvisExpr -> new("com.kotlinorm.experimental.exprtree.api.ElvisExpr", exprId(value.id.value), type(value.type), node(value.left), node(value.right), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is BlockExpr -> new("com.kotlinorm.experimental.exprtree.api.BlockExpr", exprId(value.id.value), type(value.type), list("com.kotlinorm.experimental.exprtree.api.ExprNode", value.statements.map(::node)), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is com.kotlinorm.experimental.exprtree.api.LambdaExpr -> new(
            "com.kotlinorm.experimental.exprtree.api.LambdaExpr",
            exprId(value.id.value),
            type(value.type),
            list("com.kotlinorm.experimental.exprtree.api.ParameterDecl", value.parameters.map(::parameter)),
            list("com.kotlinorm.experimental.exprtree.api.CaptureDecl", value.captures.map(::capture)),
            value.body?.let(::node) ?: nullOf("com.kotlinorm.experimental.exprtree.api.ExprNode"),
            source(value.source),
            nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"),
        )
        is LocalDeclarationExpr -> new("com.kotlinorm.experimental.exprtree.api.LocalDeclarationExpr", exprId(value.id.value), type(value.type), local(value.declaration), value.initializer?.let(::node) ?: nullOf("com.kotlinorm.experimental.exprtree.api.ExprNode"), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is AssignmentExpr -> new("com.kotlinorm.experimental.exprtree.api.AssignmentExpr", exprId(value.id.value), type(value.type), node(value.target), node(value.value), enum("com.kotlinorm.experimental.exprtree.api.AssignmentOperator", value.operator.name), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is IfExpr -> new("com.kotlinorm.experimental.exprtree.api.IfExpr", exprId(value.id.value), type(value.type), node(value.condition), node(value.thenBranch), value.elseBranch?.let(::node) ?: nullOf("com.kotlinorm.experimental.exprtree.api.ExprNode"), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is TryExpr -> new("com.kotlinorm.experimental.exprtree.api.TryExpr", exprId(value.id.value), type(value.type), node(value.tryBlock), list("com.kotlinorm.experimental.exprtree.api.CatchExpr", value.catches.map(::node)), value.finallyBlock?.let(::node) ?: nullOf("com.kotlinorm.experimental.exprtree.api.ExprNode"), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is CatchExpr -> new("com.kotlinorm.experimental.exprtree.api.CatchExpr", exprId(value.id.value), type(value.type), local(value.parameter), node(value.body), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is WhenEntryExpr -> new("com.kotlinorm.experimental.exprtree.api.WhenEntryExpr", exprId(value.id.value), type(value.type), list("com.kotlinorm.experimental.exprtree.api.ExprNode", value.conditions.map(::node)), node(value.body), builder.irBoolean(value.isElse), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is WhenExpr -> new("com.kotlinorm.experimental.exprtree.api.WhenExpr", exprId(value.id.value), type(value.type), value.subject?.let(::whenSubject) ?: nullOf("com.kotlinorm.experimental.exprtree.api.WhenSubject"), list("com.kotlinorm.experimental.exprtree.api.WhenEntryExpr", value.entries.map(::node)), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is StringTemplateExpr -> new("com.kotlinorm.experimental.exprtree.api.StringTemplateExpr", exprId(value.id.value), type(value.type), list("com.kotlinorm.experimental.exprtree.api.StringTemplatePart", value.parts.map(::stringPart)), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is TypeOperatorExpr -> new("com.kotlinorm.experimental.exprtree.api.TypeOperatorExpr", exprId(value.id.value), type(value.type), enum("com.kotlinorm.experimental.exprtree.api.TypeOperator", value.operator.name), node(value.operand), type(value.targetType), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        is UnsupportedExpr -> new("com.kotlinorm.experimental.exprtree.api.UnsupportedExpr", exprId(value.id.value), type(value.type), builder.irString(value.reason), source(value.source), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
        else -> new("com.kotlinorm.experimental.exprtree.api.UnsupportedExpr", exprId(value.id.value), type(value.type), builder.irString("KET005: generated bridge does not support ${value::class.simpleName}"), nullOf("com.kotlinorm.experimental.exprtree.api.SourceSpan"), nullOf("com.kotlinorm.experimental.exprtree.api.OriginRef"))
    }

    private fun callable(id: String, operator: Boolean, kind: CallableKind): IrExpression = new(
        "com.kotlinorm.experimental.exprtree.api.CallableRef", builder.irString(id), builder.irBoolean(operator),
        enum("com.kotlinorm.experimental.exprtree.api.CallableKind", kind.name), nullOf("kotlin.String"), nullOf("com.kotlinorm.experimental.exprtree.api.TypeRef"),
        list(anyN, emptyList()), builder.irBoolean(false), list("kotlin.String", emptyList()),
    )

    private fun type(value: com.kotlinorm.experimental.exprtree.api.TypeRef): IrExpression = new(
        "com.kotlinorm.experimental.exprtree.api.TypeRef", value.classifierId?.let(builder::irString) ?: nullOf("kotlin.String"), enum("com.kotlinorm.experimental.exprtree.api.Nullability", value.nullability.name),
        list("com.kotlinorm.experimental.exprtree.api.TypeRef", value.arguments.map(::type)), nullOf("com.kotlinorm.experimental.exprtree.api.TypeRef"), nullOf("kotlin.String"), set("com.kotlinorm.experimental.exprtree.api.TypeFlag", emptyList()),
    )

    private fun metadata(producer: String, kotlin: String, platform: String) = new(
        "com.kotlinorm.experimental.exprtree.api.TreeMetadata", builder.irString(producer), builder.irString(kotlin), builder.irString(platform), list("kotlin.String", emptyList()), emptyMap(),
    )
    private fun exprId(value: Long) = new("com.kotlinorm.experimental.exprtree.api.ExprId", builder.irLong(value))
    private fun decl(value: Long) = new("com.kotlinorm.experimental.exprtree.api.DeclId", builder.irLong(value))
    private fun constant(value: Any?): IrExpression = when (value) {
        null -> builder.irNull(anyN)
        is String -> builder.irString(value)
        is Boolean -> builder.irBoolean(value)
        is Int -> builder.irInt(value)
        is Long -> builder.irLong(value)
        is Double -> IrConstImpl.double(builder.startOffset, builder.endOffset, context.irBuiltIns.doubleType, value)
        is Float -> IrConstImpl.float(builder.startOffset, builder.endOffset, context.irBuiltIns.floatType, value)
        else -> builder.irString(value.toString())
    }

    private fun list(elementTypeName: String, values: List<IrExpression>): IrExpression =
        list(if (elementTypeName == "kotlin.Any?") anyN else klass(elementTypeName).defaultType, values)

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun list(elementType: org.jetbrains.kotlin.ir.types.IrType, values: List<IrExpression>): IrExpression {
        val function = context.finderForBuiltins().findFunctions(
            org.jetbrains.kotlin.name.CallableId(FqName("kotlin.collections"), org.jetbrains.kotlin.name.Name.identifier("listOf"))
        ).single { it.owner.parameters.size == 1 && it.owner.parameters.single().isVararg }
        return builder.irCall(function).apply {
            typeArguments[0] = elementType
            arguments[0] = builder.irVararg(elementType, values)
        }
    }

    private fun set(elementTypeName: String, values: List<IrExpression>): IrExpression {
        val elementType = klass(elementTypeName).defaultType
        val function = context.finderForBuiltins().findFunctions(
            org.jetbrains.kotlin.name.CallableId(FqName("kotlin.collections"), org.jetbrains.kotlin.name.Name.identifier("setOf"))
        ).single { it.owner.parameters.size == 1 && it.owner.parameters.single().isVararg }
        return builder.irCall(function).apply {
            typeArguments[0] = elementType
            arguments[0] = builder.irVararg(elementType, values)
        }
    }

    private fun emptyMap(): IrExpression {
        val function = context.finderForBuiltins().findFunctions(
            org.jetbrains.kotlin.name.CallableId(FqName("kotlin.collections"), org.jetbrains.kotlin.name.Name.identifier("emptyMap"))
        ).single { it.owner.parameters.isEmpty() }
        return builder.irCall(function).apply {
            typeArguments[0] = context.irBuiltIns.anyNType
            typeArguments[1] = context.irBuiltIns.anyNType
        }
    }

    private fun nullOf(name: String): IrExpression = builder.irNull(klass(name).defaultType.makeNullable())
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun enum(className: String, name: String): IrExpression {
        val entry = klass(className).owner.declarations.filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrEnumEntry>().single { it.name.asString() == name }
        return IrGetEnumValueImpl(builder.startOffset, builder.endOffset, klass(className).defaultType, entry.symbol)
    }
    private fun new(className: String, vararg args: IrExpression): IrExpression {
        val constructor = klass(className).owner.constructors.single { it.parameters.size == args.size }
        return builder.irCall(constructor.symbol).apply { args.forEachIndexed { index, arg -> arguments[index] = arg } }
    }
}
