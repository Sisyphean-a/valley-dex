package com.example.stardewoffline.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value
