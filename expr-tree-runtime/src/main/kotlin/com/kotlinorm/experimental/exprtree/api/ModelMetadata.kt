package com.kotlinorm.experimental.exprtree.api

data class OriginRef(
    val kind: OriginKind,
    val sourceIds: List<ExprId> = emptyList(),
    val note: String? = null,
)

enum class OriginKind { SOURCE, LOWERED, NORMALIZED, RECOVERED, SYNTHETIC }

enum class DiagnosticSeverity { INFO, WARNING, ERROR }

data class TreeDiagnostic(
    val code: String,
    val message: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val source: SourceSpan? = null,
    val path: List<ExprId> = emptyList(),
)

data class TreeMetadata(
    val producerVersion: String = "unknown",
    val kotlinVersion: String = "unknown",
    val targetPlatform: String = "jvm",
    val normalizationHistory: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
)
