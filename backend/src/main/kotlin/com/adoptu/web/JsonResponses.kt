package com.adoptu.web

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

// Kept in their own file, framework-import-free, so `dfk-codegen/scan` (the isolated Kotlin
// 2.3.10 + KSP composite build, see docs/codegen-consumers.md section 3 "Applying the processor
// to a Kotlin 2.4 Gradle project" in the DataFormatsKit repo) can compile this file on its own
// classpath (DataFormatsKit-jvm only) without pulling in Responses.kt's Helidon/ServiceResult
// dependencies. Both classes stay visible to Responses.kt unqualified - same package.

@JsonDecodable(strict = false)
@JsonEncodable
data class ErrorResponse(val error: String)

@JsonDecodable(strict = false)
@JsonEncodable
data class SuccessResponse(val success: Boolean)
