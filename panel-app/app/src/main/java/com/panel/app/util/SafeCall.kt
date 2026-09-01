package com.panel.app.util

import kotlinx.coroutines.CancellationException

/**
 * 在协程里执行可能抛异常的挂起函数，并把异常收敛成 `Result`。
 *
 * ### 与 `runCatching` 的区别（关键）
 * `runCatching` 会连 `CancellationException` 一起吞掉，协程因此收不到取消信号，
 * 破坏结构化并发（典型后果：ViewModel 已清空，任务还在后台跑）。
 * 这里显式把取消信号原样抛出，其余异常才转成失败结果。
 *
 * ### 为什么不装全局 CoroutineExceptionHandler
 * 全局兜底只会把崩溃变成"静默失败"，既拿不到有意义的错误提示，
 * 也会掩盖真正的空指针 / 非法参数等缺陷。
 * 正确做法是在**调用点**就地收敛：谁能给出用户可读的提示，谁就负责 catch。
 */
suspend fun <T> runSafely(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
