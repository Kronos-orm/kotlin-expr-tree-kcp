# Kotlin Expression Tree KCP

[![Kotlin](https://img.shields.io/badge/kotlin-2.4.0-%237f52ff.svg?logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![CI](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/ci.yml/badge.svg)](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/ci.yml)
[![Compiler Line Coverage](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/raw/coverage/coverage-compiler-plugin.svg)](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/coverage.yml)
[![Compiler Branch Coverage](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/raw/coverage/coverage-compiler-plugin-branch.svg)](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/coverage.yml)
[![Runtime Line Coverage](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/raw/coverage/coverage-runtime.svg)](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/coverage.yml)
[![Runtime Branch Coverage](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/raw/coverage/coverage-runtime-branch.svg)](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/coverage.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.kotlinorm.experimental/expr-tree-runtime.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.kotlinorm.experimental/expr-tree-runtime)

English | [简体中文](README-zh_CN.md)

<p align="center">
  <img src="branding/expr-tree-logo.svg" alt="Kotlin Expression Tree KCP logo" width="180">
</p>

<p align="center"><strong>Expression trees for annotated Kotlin lambdas.</strong></p>

Kotlin Expression Tree KCP is a Kotlin 2.4.0 K2/FIR compiler plugin that turns
an annotated Kotlin lambda into an expression tree represented by Kotlin
runtime model types. The tree is a sealed algebraic data type (ADT) represented
by Kotlin's `sealed interface`. Application code can inspect and transform the
tree with ordinary Kotlin `when` expressions and extension functions.

The generated tree provides the AST layer for query engines, rule engines,
policy evaluators, code generators, and other Kotlin expression-processing
systems.

## What This Library Can Do

The library provides compile-time extraction and runtime APIs for annotated
Kotlin lambdas:

- **Extract annotated Kotlin lambdas as expression trees**: the K2/FIR compiler plugin replaces the `expr` marker call at the call site with a `CapturedExpr<T, R>` containing an `ExprTree<T, R>` and the lambda's captured values.
- **Preserve resolved Kotlin semantics**: FIR resolves overloads and records the selected callable identity, receiver type, expression types, and source spans in the runtime model.
- **Model the tree as a sealed algebraic data type (ADT)**: `ExprNode` is a Kotlin `sealed interface` with data-class nodes and read-only properties for calls, operators, properties, control flow, lambdas, string templates, declarations, assignments, type operators, and unsupported expressions.
- **Bind closure captures by declaration identity**: captured values are exposed through `CaptureBindings` and can be looked up by `DeclId`, including code with shadowed names.
- **Attach captured trees to callable DSL lambdas**: `@ExprCapture` lets a lambda remain callable for in-memory execution while exposing its generated `CapturedExpr` for query, rule, or policy compilation.
- **Inspect and transform trees at runtime**: runtime APIs provide traversal, validation, source-aware diagnostics, transformations, and ABI checks over Kotlin model objects.
- **Integrate extraction with Gradle and Maven**: the supplied plugins connect the expression-tree compiler plugin to Kotlin 2.4.0 builds.

## Modules

| Module                      | Purpose                                                                                                                                    |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `expr-tree-runtime`         | Runtime API, sealed AST model (ADT), declarations, source spans, traversal, transformations, validation, capture bindings, and ABI checks. |
| `expr-tree-compiler-plugin` | Kotlin K2/FIR checker and IR call-site bridge.                                                                                             |
| `expr-tree-gradle-plugin`   | Gradle `KotlinCompilerPluginSupportPlugin` integration.                                                                                    |
| `expr-tree-maven-plugin`    | Kotlin Maven compiler plugin extension integration.                                                                                        |
| `example`                   | Example application and Kotlin DSL integration sample.                                                                                     |

## Quick Look

```kotlin
data class User(val age: Int, val name: String?)

fun predicate(minimum: Int, prefix: String) = expr<User, Boolean> { user ->
    user.age >= minimum && user.name?.startsWith(prefix) == true
}
```

At the call site, the compiler plugin generates a `CapturedExpr<User, Boolean>`
containing an `ExprTree<User, Boolean>` and its captured values:

```kotlin
val captured = predicate(18, "A")
val tree = captured.tree
val minimum = captured.bindings()[tree.captures[0].id]
```

The sealed AST can be handled exhaustively with a Kotlin `when` expression:

```kotlin
fun render(node: ExprNode): String = when (node) {
    is ConstExpr -> node.value.toString()
    is RefExpr -> node.name
    is BinaryExpr -> "(${render(node.left)} ${node.operator.callableId} ${render(node.right)})"
    is PropertyAccessExpr -> node.property.callableId
    is IndexAccessExpr -> node.callable.callableId
    is CallExpr -> node.callable.callableId
    is UnaryExpr -> node.operator.callableId
    is SafeCallExpr -> render(node.receiver) + "?."
    is ElvisExpr -> render(node.left) + " ?: " + render(node.right)
    is BlockExpr -> node.statements.joinToString("; ", transform = ::render)
    is LambdaExpr -> node.body?.let(::render).orEmpty()
    is LocalDeclarationExpr -> node.declaration.name
    is AssignmentExpr -> node.operator.name
    is IfExpr -> "if (...)"
    is ReturnExpr -> "return ${render(node.value)}"
    is WhileExpr -> "while (...)"
    is DoWhileExpr -> "do ... while (...)"
    is BreakExpr -> "break"
    is ContinueExpr -> "continue"
    is ForLoopExpr -> "for (${node.declaration.name} in ${render(node.iterable)})"
    is ThrowExpr -> "throw ${render(node.value)}"
    is DestructuringExpr -> node.entries.joinToString(", ") { it.declaration.name }
    is RangeExpr -> "${render(node.start)}..${render(node.end)}"
    is IncDecExpr -> node.operation.name
    is CallableReferenceExpr -> node.callable.callableId
    is SmartCastExpr -> render(node.expression)
    is TryExpr -> "try (${render(node.tryBlock)})"
    is CatchExpr -> "catch (${node.parameter.name}) ${render(node.body)}"
    is WhenExpr -> "when"
    is WhenEntryExpr -> "entry"
    is StringTemplateExpr -> node.parts.joinToString(transform = {
        when (it) {
            is StringTemplatePart.Text -> it.text
            is StringTemplatePart.Expression -> render(it.expression)
        }
    })
    is TypeOperatorExpr -> node.operator.name
    is UnsupportedExpr -> "recovery: ${node.reason}"
}
```

Receiver references use the same `RefExpr` shape as every other reference. Their
`kind` is `THIS` or `SUPER`; qualified receivers keep the source label in
`label`, and `super<Type>` keeps the selected type in `qualifierType`.

## Capturing Lambdas in a DSL

When a DSL function parameter is annotated with `@ExprCapture`, the plugin wraps
the lambda argument at each call site as a callable value carrying its generated
`CapturedExpr`.

```kotlin
fun <T> Query<T>.filter(@ExprCapture predicate: (T) -> Boolean): Query<T> {
    val captured = requireNotNull(predicate.capturedExprOrNull())
    return append(FilterStep(captured, predicate))
}

fun <T, R> Query<T>.map(@ExprCapture transform: (T) -> R): Query<R> {
    val captured = requireNotNull(transform.capturedExprOrNull())
    return append(MapStep(captured, transform))
}
```

The calling code remains ordinary Kotlin DSL:

```kotlin
query<User>()
    .filter { it.age >= minimumAge }
    .map { "${it.name}-$prefix" }
```

Each step in the chain carries its own tree and lexical capture bindings, and
the original function value remains callable for in-memory execution.

## Integration

The examples use version `0.1.0`. Configure a repository that contains these
artifacts, such as Maven Central or your project's artifact repository.

### Gradle Kotlin DSL

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("com.kotlinorm.experimental.expr-tree") version "0.1.0"
}

repositories { mavenCentral() }

dependencies {
    implementation("com.kotlinorm.experimental:expr-tree-runtime:0.1.0")
}
```

The Gradle plugin applies `expr-tree-compiler-plugin` to Kotlin compilations.
Add `expr-tree-runtime` to the application dependencies; the Gradle plugin supplies
the compiler integration.

### Maven

The Maven integration is registered as the Kotlin Maven extension
`expr-tree-maven-plugin`:

```xml
<dependencies>
  <dependency>
    <groupId>com.kotlinorm.experimental</groupId>
    <artifactId>expr-tree-runtime</artifactId>
    <version>0.1.0</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-maven-plugin</artifactId>
      <version>2.4.0</version>
      <extensions>true</extensions>
      <configuration>
        <compilerPlugins>
          <plugin>expr-tree-maven-plugin</plugin>
        </compilerPlugins>
      </configuration>
      <dependencies>
        <dependency>
          <groupId>com.kotlinorm.experimental</groupId>
          <artifactId>expr-tree-maven-plugin</artifactId>
          <version>0.1.0</version>
        </dependency>
      </dependencies>
    </plugin>
  </plugins>
</build>
```

### Local Development

The `example` module uses the Gradle plugin from the included build and provides
a ready-to-run Kotlin DSL integration sample:

```powershell
.\gradlew.bat :example:build --no-daemon --rerun-tasks
```

## Supported Expression Forms

The compiler-plugin test fixtures cover literals, parameter, local-variable,
and captured-value references; property access; function and operator calls;
boolean logic; comparisons; equality checks; safe calls; Elvis expressions;
blocks; nested lambdas; local declarations; assignments; expression-valued
`if`; `try` expressions with catch clauses and finally blocks; subjectless and subject-style `when`; string templates;
`is`/`!is`/`as`/`as?`; source spans; shadowing; and labelled control transfers.
Operator nodes retain the resolved callable ID and source operator token. Calls retain
dispatch receivers, extension receivers, ordinary arguments, context arguments, and
callable context-parameter metadata. A subject-style
`when (value)` is represented by a `WhenSubject` that stores the initializer
once and provides a stable local declaration for branch conditions.
Positional destructuring is represented by one `DestructuringExpr` whose bindings
retain declaration order and FIR `componentIndex`; name-based destructuring uses the
same node with property-name metadata.

Runtime APIs expose the tree to domain adapters, analyzers, transformers, and
code generators. Adapters can use source spans and AST paths to report
diagnostics.

## Requirements

| Dependency | Version                                                 |
|------------|---------------------------------------------------------|
| JDK        | 17+ for building; generated JVM bytecode targets Java 8 |
| Kotlin     | 2.4.0                                                   |
| Gradle     | 9.6.1 wrapper included                                  |
| Maven      | 3.9+ for the Maven integration                          |

## Build and Test

```powershell
.\gradlew.bat check --no-daemon --rerun-tasks
.\gradlew.bat :expr-tree-runtime:test --no-daemon --rerun-tasks
.\gradlew.bat :example:build --no-daemon --rerun-tasks
```

## License

MIT License. See [LICENSE](LICENSE).
