package com.quickledger.app.shizuku

import android.content.Context
import com.quickledger.app.accessibility.AccessibilityCaptureService
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * 方式二的 Shizuku 部分：
 * 用于免手动操作，直接通过 shell 打开本应用的无障碍服务（推荐路径）。
 */
object ShizukuHelper {

    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 9001

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun ping(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        ping() && Shizuku.checkSelfPermission() ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!ping()) {
            onResult(false)
            return
        }
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                onResult(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE)
    }

    /**
     * 通过 Shizuku（shell uid）直接启用本应用的无障碍服务。
     * @return 是否执行成功
     */
    fun enableAccessibilityViaShizuku(context: Context): Boolean {
        if (!granted()) return false
        return try {
            val component = "${context.packageName}/${AccessibilityCaptureService::class.java.name}"
            val current = runShell("settings get secure enabled_accessibility_services")
                ?.trim().orEmpty()
            val enabled = current.split(":").filter { it.isNotBlank() }
            val newValue = (enabled + component).distinct().joinToString(":")
            runShell("settings put secure enabled_accessibility_services \"$newValue\"")
            runShell("settings put secure accessibility_enabled 1")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return flat.split(":").any {
            it.equals(AccessibilityCaptureService.serviceName(), ignoreCase = true)
        }
    }

    @Throws(Throwable::class)
    private fun runShell(cmd: String): String? {
        val service = ShizukuAccessor.require()
        val process = service.newProcess(arrayOf("sh", "-c", cmd), null, null)
        val out = android.os.ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
            .bufferedReader().readText()
        process.waitFor()
        return out
    }
}

/** 暴露 Shizuku.requireService()（protected）给本应用使用 */
internal class ShizukuAccessor : Shizuku() {
    companion object {
        fun require(): IShizukuService = requireService()
    }
}
