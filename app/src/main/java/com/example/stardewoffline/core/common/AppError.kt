package com.example.stardewoffline.core.common

sealed interface AppError {
    val message: String

    data object NoDataPackage : AppError { override val message = "尚未导入数据包" }
    data class InvalidPackageFormat(val detail: String) : AppError { override val message = "数据包格式无效：$detail" }
    data class UnsupportedSchema(val version: Int) : AppError { override val message = "数据包 schema $version 暂不受支持" }
    data class InvalidManifest(val detail: String) : AppError { override val message = "数据包清单无效：$detail" }
    data object NotPublishable : AppError { override val message = "数据包不是可发布版本" }
    data class QualityFailed(val status: String, val dataErrors: Int) : AppError { override val message = "数据质量未通过：$status，错误 $dataErrors" }
    data class MetadataMismatch(val field: String) : AppError { override val message = "数据包元数据不一致：$field" }
    data class InvalidEntityTypeCatalog(val detail: String) : AppError { override val message = "实体类型目录无效：$detail" }
    data object HashMismatch : AppError { override val message = "数据库校验和不匹配" }
    data class DatabaseCorrupted(val detail: String) : AppError { override val message = "数据库校验失败：$detail" }
    data class DatabaseOpenFailed(val detail: String) : AppError { override val message = "无法打开内容数据库：$detail" }
    data class DatabaseQueryFailed(val detail: String) : AppError { override val message = "内容数据库查询失败：$detail" }
    data class UnsafeArchiveEntry(val entry: String) : AppError { override val message = "数据包包含不安全文件：$entry" }
    data class PackageTooLarge(val detail: String) : AppError { override val message = "数据包过大：$detail" }
    data object ImportCancelled : AppError { override val message = "已取消导入" }
    data class ImageMissing(val imagePath: String) : AppError { override val message = "图片不存在：$imagePath" }
    data class JsonParseFailed(val detail: String) : AppError { override val message = "JSON 解析失败：$detail" }
    data class Unknown(val detail: String) : AppError { override val message = "发生未知错误：$detail" }
}
