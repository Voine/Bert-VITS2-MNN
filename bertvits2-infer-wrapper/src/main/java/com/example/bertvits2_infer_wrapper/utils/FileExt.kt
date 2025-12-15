package com.example.bertvits2_infer_wrapper.utils

import android.content.Context
import kotlinx.coroutines.CancellableContinuation
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * @param src asset file
 * @param des target dir
 */
fun Context.copyAssets2Local(
        deleteIfExists: Boolean = true,
        srcPath: String,
        desPath: String,
        callBack: ((isSuccess: Boolean, absPath: String) -> Unit)? = null
) {
    val localFile = File(desPath, srcPath)
    if (deleteIfExists && localFile.exists()) {
        FileUtils.deleteDirectory(localFile)
    }

    try {
        val targetDir = File(desPath)
        FileUtils.copyAssets(
            assets,
            srcPath,
            targetDir.absolutePath,
            ByteArray(FileUtils.DEFAULT_BUFFER)
        )
        callBack?.invoke(true, targetDir.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
        FileUtils.deleteDirectory(localFile)
        callBack?.invoke(false, "")
    }
}

fun <T> CancellableContinuation<T>.safeResume(value: T) {
    if (this.isActive) {
        (this as? Continuation<T>)?.resume(value)
    }
}