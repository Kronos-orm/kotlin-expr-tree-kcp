# Kotlin Expression Tree KCP

[![Kotlin](https://img.shields.io/badge/kotlin-2.4.0-%237f52ff.svg?logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![CI](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/ci.yml/badge.svg)](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/ci.yml)
[![Coverage](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/raw/coverage/coverage-compiler-plugin.svg)](https://github.com/Kronos-orm/kotlin-expr-tree-kcp/actions/workflows/coverage.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.kotlinorm.experimental/expr-tree-runtime.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.kotlinorm.experimental/expr-tree-runtime)

[English](README.md) | 简体中文

<p align="center">
  <img src="branding/expr-tree-logo.svg" alt="Kotlin Expression Tree KCP logo" width="180">
</p>

<p align="center"><strong>为带标记的 Kotlin lambda 提取表达式树。</strong></p>

Kotlin Expression Tree KCP 是一个面向 Kotlin 2.4.0 K2/FIR 的编译器插件，用于将带标记的
Kotlin lambda 提取为由 Kotlin 运行时模型表示的表达式树。表达式树以 Kotlin 密封类型层次
（`sealed interface`）实现代数数据类型（ADT），应用代码可以通过普通 Kotlin `when` 表达式
逐一处理和变换所有节点类型。

生成的表达式树可以作为查询引擎、规则引擎、策略评估器、代码生成器以及其他 Kotlin
表达式处理系统的 AST 基础。

## 本库能做些什么

本库为带标记的 Kotlin lambda 提供编译期提取和运行时 API：

- **把带标记的 Kotlin lambda 提取为表达式树**：K2/FIR 编译器插件会在调用点替换 `expr` 标记调用，生成 `CapturedExpr<T, R>`，其中包含 `ExprTree<T, R>` 和 lambda 闭包捕获的值。
- **保留 Kotlin 编译器解析出的语义**：FIR 负责解析调用，并在运行时模型中记录最终选中的可调用符号、接收者类型（receiver type）、Kotlin 类型和源码区间。
- **使用 Kotlin 密封类型（`sealed interface`）和代数数据类型（ADT）建模表达式树**：`ExprNode` 由带只读属性的数据类节点组成，表示函数调用、操作符、属性访问、控制流、lambda、字符串模板、声明、赋值、类型操作，以及提取器的恢复结果节点（`UnsupportedExpr`）。
- **按声明身份绑定闭包捕获值**：捕获值通过 `CaptureBindings` 提供，并可使用 `DeclId` 查找；名称遮蔽场景也能保持绑定关系。
- **让 DSL lambda 保持可调用并暴露关联的表达式树**：使用 `@ExprCapture` 后，lambda 继续支持直接调用，并携带生成的 `CapturedExpr`，用于查询、规则或策略编译。
- **在运行时检查和变换表达式树**：运行时 API 基于 Kotlin 模型对象提供遍历、校验、带源码定位的诊断、变换，以及 ABI 版本和树 schema 版本校验。
- **通过 Gradle 和 Maven 接入编译期提取**：随附插件可以把表达式树编译插件接入 Kotlin 2.4.0 构建。

## 模块

| 模块                        | 职责                                                                                                                |
|-----------------------------|---------------------------------------------------------------------------------------------------------------------|
| `expr-tree-runtime`         | 运行时 API、基于 Kotlin `sealed interface` 的 AST/ADT 模型、声明、源码区间、遍历、变换、校验、捕获绑定和 ABI 校验。 |
| `expr-tree-compiler-plugin` | Kotlin K2/FIR 检查器（checker）和 IR 调用点桥接。                                                                   |
| `expr-tree-gradle-plugin`   | Gradle `KotlinCompilerPluginSupportPlugin` 集成。                                                                   |
| `expr-tree-maven-plugin`    | Kotlin Maven 编译器插件扩展集成。                                                                                   |
| `example`                   | 示例应用模块和 Kotlin DSL 集成示例。                                                                                |

## 快速示例

```kotlin
data class User(val age: Int, val name: String?)

fun predicate(minimum: Int, prefix: String) = expr<User, Boolean> { user ->
    user.age >= minimum && user.name?.startsWith(prefix) == true
}
```

编译器插件会在调用点生成包含捕获值的 `CapturedExpr<User, Boolean>`，其中包含
`ExprTree<User, Boolean>`：

```kotlin
val captured = predicate(18, "A")
val tree = captured.tree
val minimum = captured.bindings()[tree.captures[0].id]
```

可以使用 Kotlin `when` 表达式逐一处理密封 AST 的所有节点类型：

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

## 在 DSL 中捕获 Lambda

当 DSL 函数参数标记为 `@ExprCapture` 时，编译器插件会在每个调用点将传入的 lambda
包装为携带其 `CapturedExpr` 的可调用对象。

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

应用代码继续使用普通 Kotlin DSL：

```kotlin
query<User>()
    .filter { it.age >= minimumAge }
    .map { "${it.name}-$prefix" }
```

链式调用的每一步都会携带自己的 AST 和词法捕获绑定，函数对象也可直接执行。

## 接入方式

以下示例使用版本 `0.1.0`；请配置包含这些制品的仓库，例如 Maven Central 或项目使用的实际制品仓库。

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

Gradle 插件会把 `expr-tree-compiler-plugin` 加入 Kotlin 编译任务。应用代码添加
`expr-tree-runtime` 依赖，编译期提取由 Gradle 插件接入。

### Maven

Maven 集成通过 Kotlin Maven 编译器插件扩展 `expr-tree-maven-plugin` 注册：

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

### 本地开发

仓库中的 `example` 模块通过本地包含构建（included build）使用 Gradle 插件，提供可直接运行的 Kotlin DSL 集成示例：

```powershell
.\gradlew.bat :example:build --no-daemon --rerun-tasks
```

## 当前支持的表达式

编译器插件的测试样例（test fixtures）覆盖字面量、参数引用、局部变量引用、捕获值引用、属性访问、
函数和操作符调用、布尔逻辑、比较、相等判断、安全调用（safe call）、Elvis 表达式、
代码块（block）、嵌套 Lambda、局部声明、赋值、表达式形式的 `if`、无 subject 和有
subject 的 `when`、带 catch 子句和 finally 块的 `try` 表达式、字符串模板、`is`/`!is`/`as`/`as?`、源码区间和名称遮蔽（shadowing）。

`when (value) { ... }` 会表示为 `WhenSubject`：树中保存一份 initializer，并提供稳定的局部
声明供分支条件引用。

运行时 API 为领域适配器、分析器、变换器和代码生成器提供表达式树访问能力。适配器可以
使用源码区间和 AST 路径生成诊断。

## 环境要求

| 依赖   | 版本                                         |
|--------|----------------------------------------------|
| JDK    | 构建需要 17+；生成的 JVM 字节码目标为 Java 8 |
| Kotlin | 2.4.0                                        |
| Gradle | 仓库内置 Gradle Wrapper 9.6.1                |
| Maven  | 3.9+（Maven 集成）                           |

## 构建与测试

```powershell
.\gradlew.bat check --no-daemon --rerun-tasks
.\gradlew.bat :expr-tree-runtime:test --no-daemon --rerun-tasks
.\gradlew.bat :example:build --no-daemon --rerun-tasks
```

## 许可证

MIT License，详见 [LICENSE](LICENSE)。
