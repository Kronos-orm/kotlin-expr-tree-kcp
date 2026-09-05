# Kotlin Expression Tree KCP

[English](README.md) | 简体中文

Kotlin Expression Tree KCP 是一个面向 Kotlin 2.4.0 K2/FIR 的编译器插件，
用于把带标记的 Kotlin lambda 提取为与编译器无关的表达式树。表达式树采用
sealed ADT 设计，消费方可以直接使用普通 Kotlin `when` 做穷举匹配和变换。

runtime AST 为查询引擎、规则引擎、策略系统、代码生成器和其他 Kotlin 表达式消费者
提供统一基础。

## 设计重点

- **直接写 Kotlin**：输入就是日常使用的 Kotlin 表达式。
- **编译期提取**：由 FIR 解析符号、重载、receiver 和类型信息。
- **封闭运行时模型**：`ExprNode` 是 `sealed interface`，节点是不可变数据类。
- **正确处理捕获**：闭包值按稳定的声明身份绑定。
- **可移植 runtime**：消费方直接使用带源码信息的 Kotlin 模型对象。
- **锁定 Kotlin 2.4.0**：首版只支持一条 K2 编译器版本线，保证行为可复现。

## 模块

| 模块 | 职责 |
| --- | --- |
| `expr-tree-runtime` | 公开 API、sealed AST、声明、源码区间、遍历、变换、校验、捕获绑定和 ABI 校验。 |
| `expr-tree-compiler-plugin` | Kotlin K2/FIR checker 和 IR 调用点桥接。 |
| `expr-tree-gradle-plugin` | Gradle `KotlinCompilerPluginSupportPlugin` 集成。 |
| `expr-tree-maven-plugin` | Kotlin Maven compiler-plugin extension 集成。 |
| `example` | 本地消费者、端到端编译 fixture，以及 test scope 的 query-adapter 演示。 |

## 快速示例

```kotlin
data class User(val age: Int, val name: String?)

fun predicate(minimum: Int, prefix: String) = expr<User, Boolean> { user ->
    user.age >= minimum && user.name?.startsWith(prefix) == true
}
```

编译器会在调用点替换 marker 调用。运行时结果包含 `ExprTree<User, Boolean>`
和捕获值：

```kotlin
val captured = predicate(18, "A")
val tree = captured.tree
val minimum = captured.bindings()[tree.captures[0].id]
```

消费方可以对 AST 做穷举处理：

```kotlin
fun nodeName(node: ExprNode): String = when (node) {
    is ConstExpr -> "const"
    is RefExpr -> node.name
    is BinaryExpr -> node.operator.callableId
    is PropertyGetExpr -> node.property.callableId
    is CallExpr -> node.callable.callableId
    is UnaryExpr -> node.operator.callableId
    is SafeCallExpr -> "safe-call"
    is ElvisExpr -> "elvis"
    is BlockExpr -> "block"
    is LambdaExpr -> "lambda"
    is LocalDeclarationExpr -> node.declaration.name
    is AssignmentExpr -> node.operator.name
    is IfExpr -> "if"
    is WhenExpr -> "when"
    is WhenEntryExpr -> "when-entry"
    is StringTemplateExpr -> "string-template"
    is TypeOperatorExpr -> node.operator.name
    is UnsupportedExpr -> "recovery"
}
```

## 接入方式

以下坐标对应当前仓库的构建产物；发布后请将 `0.1.0-SNAPSHOT` 替换为正式版本，
并配置实际的制品仓库。

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

Gradle 插件会把 `expr-tree-compiler-plugin` 加入 Kotlin 编译任务。业务源码使用
`expr-tree-runtime`，编译集成由 Gradle 插件提供。

### Maven

Maven 集成以 Kotlin Maven extension `expr-tree-maven-plugin` 注册：

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

### 本地开发

仓库内的 `example` 模块会直接加载 compiler-plugin JAR，提供可直接运行的本地接入环境：

```powershell
.\gradlew.bat :example:test --no-daemon --rerun-tasks
```

## 当前支持的表达式

当前 fixture 已覆盖字面量、参数/局部变量/捕获引用、属性读取、函数和操作符调用、
布尔逻辑、比较、相等、safe-call、Elvis、block、嵌套 lambda、局部声明、赋值、
表达式形式的 `if`、有 subject 和无 subject 的 `when`、字符串模板、
`is`/`!is`/`as`/`as?`、源码区间和 shadowing。

`when (value) { ... }` 会生成 `WhenSubject`：initializer 只执行一次，分支条件通过
稳定的 local declaration 引用 subject，并将 FIR 合成符号收敛为 runtime 模型。

提取结果可直接用于适配、分析、变换和代码生成。具体领域 backend 可以使用源码区间和
AST 路径提供自身的诊断和能力报告。

## 环境要求

| 依赖 | 版本 |
| --- | --- |
| JDK | 8+；本仓库使用 JDK 17 完成验证 |
| Kotlin | 2.4.0 |
| Gradle | 仓库内置 9.6.1 wrapper；也可使用兼容版本 |
| Maven | 3.9+（Maven 集成） |

## 构建与测试

```powershell
.\gradlew.bat check --no-daemon --rerun-tasks
.\gradlew.bat :expr-tree-runtime:test --no-daemon --rerun-tasks
.\gradlew.bat :example:test --no-daemon --rerun-tasks
```

## License

Apache License 2.0，详见 [LICENSE](LICENSE)。
