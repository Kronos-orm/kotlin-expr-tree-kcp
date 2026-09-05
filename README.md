# Kotlin Expression Tree KCP

English | [简体中文](README-zh_CN.md)

Kotlin Expression Tree KCP is a Kotlin 2.4.0 K2/FIR compiler plugin that turns
an annotated Kotlin lambda into a compiler-independent expression tree. The
tree is a sealed algebraic data type, so a consumer can inspect and transform
it with ordinary Kotlin `when` expressions and extension functions.

The runtime tree is a foundation for query engines, rules engines, policy
evaluators, code generators, and other Kotlin-native expression consumers.

## Why This Project

- **Kotlin-native input**: write normal Kotlin expressions in the language you already use.
- **Compile-time extraction**: FIR resolves symbols, overloads, receivers, and types.
- **Closed runtime model**: `ExprNode` is a `sealed interface` with immutable data nodes.
- **Correct captures**: closure values are bound by stable declaration identity.
- **Portable runtime**: runtime consumers receive plain Kotlin model objects with source information.
- **Kotlin 2.4.0 baseline**: the initial plugin targets one K2 compiler line for predictable behavior.

## Modules

| Module | Purpose |
| --- | --- |
| `expr-tree-runtime` | Public API, sealed AST, declarations, source spans, traversal, transforms, validation, capture binding, and ABI checks. |
| `expr-tree-compiler-plugin` | Kotlin K2/FIR checker and IR call-site bridge. |
| `expr-tree-gradle-plugin` | Gradle `KotlinCompilerPluginSupportPlugin` integration. |
| `expr-tree-maven-plugin` | Kotlin Maven compiler-plugin extension integration. |
| `example` | Local consumer, end-to-end compiler fixtures, and a test-scope query-adapter demonstration. |

## Quick Look

```kotlin
data class User(val age: Int, val name: String?)

fun predicate(minimum: Int, prefix: String) = expr<User, Boolean> { user ->
    user.age >= minimum && user.name?.startsWith(prefix) == true
}
```

The compiler replaces the marker call at the call site. At runtime, the result
contains an `ExprTree<User, Boolean>` and the captured values:

```kotlin
val captured = predicate(18, "A")
val tree = captured.tree
val minimum = captured.bindings()[tree.captures[0].id]
```

The AST can be consumed exhaustively:

```kotlin
fun render(node: ExprNode): String = when (node) {
    is ConstExpr -> node.value.toString()
    is RefExpr -> node.name
    is BinaryExpr -> "(${render(node.left)} ${node.operator.callableId} ${render(node.right)})"
    is PropertyGetExpr -> node.property.callableId
    is CallExpr -> node.callable.callableId
    is UnaryExpr -> node.operator.callableId
    is SafeCallExpr -> render(node.receiver) + "?."
    is ElvisExpr -> render(node.left) + " ?: " + render(node.right)
    is BlockExpr -> node.statements.joinToString("; ", transform = ::render)
    is LambdaExpr -> node.body?.let(::render).orEmpty()
    is LocalDeclarationExpr -> node.declaration.name
    is AssignmentExpr -> node.operator.name
    is IfExpr -> "if (...)"
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

## Integration

The snippets below use the coordinates produced by this repository. Replace
`0.1.0-SNAPSHOT` with the released version and add the repository that hosts
your build artifacts.

### Gradle Kotlin DSL

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("com.kotlinorm.experimental.expr-tree") version "0.1.0-SNAPSHOT"
}

repositories { mavenCentral() }

dependencies {
    implementation("com.kotlinorm.experimental:expr-tree-runtime:0.1.0-SNAPSHOT")
}
```

The Gradle plugin applies `expr-tree-compiler-plugin` to Kotlin compilations.
Application source uses `expr-tree-runtime`; the Gradle plugin supplies the compiler integration.

### Maven

The Maven integration is registered as the Kotlin Maven extension
`expr-tree-maven-plugin`:

```xml
<dependencies>
  <dependency>
    <groupId>com.kotlinorm.experimental</groupId>
    <artifactId>expr-tree-runtime</artifactId>
    <version>0.1.0-SNAPSHOT</version>
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
          <version>0.1.0-SNAPSHOT</version>
        </dependency>
      </dependencies>
    </plugin>
  </plugins>
</build>
```

### Local Development

The included `example` module loads the compiler-plugin JAR directly and provides
a ready-to-run local integration environment:

```powershell
.\gradlew.bat :example:test --no-daemon --rerun-tasks
```

## Supported Expression Forms

The current fixtures cover literals, parameter/local/capture references,
property reads, function and operator calls, boolean logic, comparisons,
equality, safe calls, Elvis, blocks, nested lambdas, local declarations,
assignments, expression-valued `if`, subjectless and subject-style `when`,
string templates, `is`/`!is`/`as`/`as?`, source spans, and shadowing. A
subject-style `when (value)` is represented by a
`WhenSubject` containing one initializer and a stable local declaration used by
branch conditions.

The extracted tree is ready for adaptation, analysis, transformation, and code
generation. Domain backends can use source spans and AST paths for their own
diagnostics and capability reporting.

## Requirements

| Dependency | Version |
| --- | --- |
| JDK | 8+; this repository is verified with JDK 17 |
| Kotlin | 2.4.0 |
| Gradle | 9.6.1 wrapper included; compatible Gradle versions may be used |
| Maven | 3.9+ for the Maven integration |

## Build and Test

```powershell
.\gradlew.bat check --no-daemon --rerun-tasks
.\gradlew.bat :expr-tree-runtime:test --no-daemon --rerun-tasks
.\gradlew.bat :example:test --no-daemon --rerun-tasks
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
