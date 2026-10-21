package com.god.ApplicationManager.privilege

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.god.ApplicationManager.admin.AppAdminReceiver
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

/** Which mechanism performs privileged operations. Priority: DEVICE_OWNER > ROOT > SHIZUKU > NON_ROOT. */
enum class PrivilegeBackend { DEVICE_OWNER, ROOT, SHIZUKU, NON_ROOT }

/**
 * One privileged operation surface for the whole app.
 *
 * All methods BLOCK and must be called from a background thread. They return
 * true only when the operation really succeeded (real exit status / API
 * result), never on a fire-and-forget basis.
 */
interface PrivilegedExecutor {
    val backend: PrivilegeBackend
    fun isAvailable(context: Context): Boolean

    /** Freeze: disable (shell) or suspend (device owner). */
    fun freeze(context: Context, packageName: String): Boolean

    /** Undo [freeze]. */
    fun unfreeze(context: Context, packageName: String): Boolean
    fun forceStop(context: Context, packageName: String): Boolean

    /** Uninstall for the current user (shell backends only). */
    fun uninstallForUser(context: Context, packageName: String): Boolean

    /** The freeze-all combo used historically: standby + force-stop + kill. */
    fun standbyAndStop(context: Context, packageName: String): Boolean
}

/** waitFor-with-timeout that works on API 24/25 (Process.waitFor(timeout) needs API 26). */
internal fun waitForTimeout(process: Process, timeoutSeconds: Long): Boolean {
    val waiter = Thread {
        try {
            process.waitFor()
        } catch (ignored: InterruptedException) {
        }
    }
    waiter.isDaemon = true
    waiter.start()
    waiter.join(timeoutSeconds * 1000)
    if (waiter.isAlive) {
        process.destroy()
        return false
    }
    return true
}

/** Runs commands in a root shell via our own `su` process (replaces libsuperuser). */
object RootShell {
    private const val TAG = "RootShell"
    private const val TIMEOUT_SECONDS = 30L

    @JvmStatic
    fun run(command: String): Boolean = runWithOutput(command) != null

    /** Returns captured stdout lines, or null when su is missing/denied or the command failed. */
    @JvmStatic
    fun runWithOutput(command: String): List<String>? {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            // Drain stdout concurrently: reading only after exit can deadlock
            // once the pipe buffer fills, and reading to EOF first would make
            // the timeout unenforceable.
            val output = java.util.Collections.synchronizedList(mutableListOf<String>())
            val reader = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).forEachLine { output.add(it) }
                } catch (ignored: Exception) {
                }
            }
            reader.isDaemon = true
            reader.start()
            if (!waitForTimeout(process, TIMEOUT_SECONDS)) {
                Log.e(TAG, "Root command timed out: $command")
                return null
            }
            reader.join(1000)
            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            Log.e(TAG, "Root command failed: $command (${e.message})")
            null
        }
    }

    @JvmStatic
    fun checkRoot(): Boolean {
        val output = runWithOutput("id")
        return output != null && output.any { it.contains("uid=0") }
    }
}

/** Shell through Shizuku's privileged process (user-granted, no root). */
object ShizukuShell {
    private const val TAG = "ShizukuShell"

    @JvmStatic
    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    @JvmStatic
    fun hasPermission(): Boolean = try {
        isBinderAlive() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    @JvmStatic
    fun runWithOutput(command: String): List<String>? {
        if (!hasPermission()) return null
        return try {
            val method = Shizuku::class.java.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val output = java.util.Collections.synchronizedList(mutableListOf<String>())
            val reader = Thread {
                try {
                    java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                        .forEachLine { output.add(it) }
                } catch (ignored: Exception) {
                }
            }
            reader.isDaemon = true
            reader.start()
            if (!waitForTimeout(process, 30)) {
                null
            } else {
                reader.join(1000)
                if (process.exitValue() == 0) output else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku command failed: $command (${e.message})")
            null
        }
    }

    @JvmStatic
    fun run(command: String): Boolean {
        if (!hasPermission()) return false
        return try {
            // Shizuku.newProcess is public in the API surface but accessed via
            // reflection so a future API reshuffle degrades to "unavailable"
            // instead of a linkage crash.
            val method = Shizuku::class.java.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null,
            ) as Process
            if (!waitForTimeout(process, 30)) {
                false
            } else {
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku command failed: $command (${e.message})")
            false
        }
    }
}

private class ShellExecutor(
    override val backend: PrivilegeBackend,
    private val runner: (String) -> Boolean,
    private val available: (Context) -> Boolean,
) : PrivilegedExecutor {
    override fun isAvailable(context: Context): Boolean = available(context)
    override fun freeze(context: Context, packageName: String): Boolean = runner("pm disable $packageName")
    override fun unfreeze(context: Context, packageName: String): Boolean = runner("pm enable $packageName")
    override fun forceStop(context: Context, packageName: String): Boolean = runner("am force-stop $packageName")
    override fun uninstallForUser(context: Context, packageName: String): Boolean =
        runner("pm uninstall --user 0 $packageName")

    override fun standbyAndStop(context: Context, packageName: String): Boolean {
        runner("am set-inactive $packageName true")
        val stopped = runner("am force-stop $packageName")
        runner("am kill $packageName")
        return stopped
    }
}

/** Device Owner backend: real freeze via package suspension; no shell operations. */
private class DeviceOwnerExecutor : PrivilegedExecutor {
    override val backend: PrivilegeBackend = PrivilegeBackend.DEVICE_OWNER

    override fun isAvailable(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    private fun admin(context: Context) = ComponentName(context, AppAdminReceiver::class.java)

    override fun freeze(context: Context, packageName: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val failed = dpm.setPackagesSuspended(admin(context), arrayOf(packageName), true)
        return failed.isEmpty()
    }

    override fun unfreeze(context: Context, packageName: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val failed = dpm.setPackagesSuspended(admin(context), arrayOf(packageName), false)
        return failed.isEmpty()
    }

    override fun forceStop(context: Context, packageName: String): Boolean = false
    override fun uninstallForUser(context: Context, packageName: String): Boolean = false
    override fun standbyAndStop(context: Context, packageName: String): Boolean = freeze(context, packageName)
}

private object NonRootExecutor : PrivilegedExecutor {
    override val backend: PrivilegeBackend = PrivilegeBackend.NON_ROOT
    override fun isAvailable(context: Context): Boolean = true
    override fun freeze(context: Context, packageName: String): Boolean = false
    override fun unfreeze(context: Context, packageName: String): Boolean = false
    override fun forceStop(context: Context, packageName: String): Boolean = false
    override fun uninstallForUser(context: Context, packageName: String): Boolean = false
    override fun standbyAndStop(context: Context, packageName: String): Boolean = false
}

/** Picks the strongest available backend and caches the root probe. */
object PrivilegeManager {
    private val rootExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    var rootAvailable: Boolean = false
        private set

    private val deviceOwner = DeviceOwnerExecutor()
    private val root = ShellExecutor(PrivilegeBackend.ROOT, RootShell::run) { rootAvailable }
    private val shizuku = ShellExecutor(PrivilegeBackend.SHIZUKU, ShizukuShell::run) { ShizukuShell.hasPermission() }
    private val nonRoot = NonRootExecutor

    @JvmStatic
    fun current(context: Context): PrivilegedExecutor = when {
        deviceOwner.isAvailable(context) -> deviceOwner
        rootAvailable -> root
        shizuku.isAvailable(context) -> shizuku
        else -> nonRoot
    }

    /** Runs a privileged shell command on the best shell backend (root, then Shizuku). */
    @JvmStatic
    fun runShellCommand(context: Context, command: String): Boolean = when {
        rootAvailable -> RootShell.run(command)
        ShizukuShell.hasPermission() -> ShizukuShell.run(command)
        else -> false
    }

    /** Output-returning variant of [runShellCommand]; null when no shell backend or the command failed. */
    @JvmStatic
    fun runShellCommandWithOutput(context: Context, command: String): List<String>? = when {
        rootAvailable -> RootShell.runWithOutput(command)
        ShizukuShell.hasPermission() -> ShizukuShell.runWithOutput(command)
        else -> null
    }

    @JvmStatic
    fun refreshRootAsync() {
        rootExecutorService.execute {
            rootAvailable = RootShell.checkRoot()
        }
    }

    /** Blocking root probe; call from a background thread only. */
    @JvmStatic
    fun refreshRootBlocking(): Boolean {
        rootAvailable = RootShell.checkRoot()
        return rootAvailable
    }
}
