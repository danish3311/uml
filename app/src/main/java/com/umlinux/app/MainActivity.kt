package com.umlinux.app

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import kotlin.concurrent.thread

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private lateinit var startStopButton: Button
    private lateinit var statusText: TextView
    private lateinit var pickRootfsButton: Button
    private lateinit var downloadRootfsButton: Button
    private var session: TerminalSession? = null

    private val assetsDir by lazy { File(filesDir, "uml") }

    private val pickRootfsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importRootfsFromPicker(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalView = findViewById(R.id.terminalView)
        startStopButton = findViewById(R.id.startStopButton)
        statusText = findViewById(R.id.statusText)
        pickRootfsButton = findViewById(R.id.pickRootfsButton)
        downloadRootfsButton = findViewById(R.id.downloadRootfsButton)

        terminalView.setTerminalViewClient(object : TerminalViewClient {
            override fun onScale(scale: Float) = 1.0f
            override fun onSingleTapUp(e: android.view.MotionEvent?) {}
            override fun shouldBackButtonBeMappedToEscape() = false
            override fun shouldEnforceCharBasedInput() = true
            override fun shouldUseCtrlSpaceWorkaround() = false
            override fun isTerminalViewSelected() = true
            override fun copyModeChanged(copyMode: Boolean) {}
            override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?) = false
            override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
            override fun onLongPress(e: android.view.MotionEvent?) = false
            override fun readControlKey() = false
            override fun readAltKey() = false
            override fun readShiftKey() = false
            override fun readFnKey() = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?) = false
            override fun onEmulatorSet() {}
            override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
            override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
            override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
            override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        })

        startStopButton.setOnClickListener {
            if (session == null) startBoot() else stopBoot()
        }
        pickRootfsButton.setOnClickListener { pickRootfsLauncher.launch(arrayOf("*/*")) }
        downloadRootfsButton.setOnClickListener { downloadRootfs() }

        // Get the always-bundled binaries out of the APK and see whether a
        // rootfs is already available (bundled, or left over from a
        // previous pick/download) before the user touches anything.
        thread {
            try {
                BundledAssets.ensureAll(assets, assetsDir)
                BundledAssets.tryExtractRootfs(assets, assetsDir)
            } catch (e: Exception) {
                Log.e(TAG, "startup asset prep failed", e)
            }
            runOnUiThread { refreshRootfsUi() }
        }
    }

    /** Reflects current rootfs availability in the UI. Safe to call from the main thread anytime. */
    private fun refreshRootfsUi() {
        val haveRootfs = RootfsManager.targetFile(assetsDir).exists()
        startStopButton.isEnabled = haveRootfs && session == null
        pickRootfsButton.isEnabled = session == null
        downloadRootfsButton.isEnabled = session == null
        if (session == null) {
            statusText.text = getString(
                if (haveRootfs) R.string.status_idle else R.string.status_missing_rootfs
            )
        }
    }

    private fun importRootfsFromPicker(uri: Uri) {
        setRootfsButtonsEnabled(false)
        statusText.text = getString(R.string.status_importing_rootfs)

        thread {
            try {
                val displayName = queryDisplayName(uri)
                RootfsManager.copyFromUri(contentResolver, uri, assetsDir, displayName)
                runOnUiThread {
                    statusText.text = getString(R.string.status_rootfs_ready)
                    refreshRootfsUi()
                }
            } catch (e: Exception) {
                Log.e(TAG, "rootfs import failed", e)
                runOnUiThread {
                    statusText.text = "Import failed: ${e.message}"
                    refreshRootfsUi()
                }
            }
        }
    }

    private fun downloadRootfs() {
        setRootfsButtonsEnabled(false)

        thread {
            try {
                RootfsManager.download(RootfsManager.DEFAULT_DOWNLOAD_URL, assetsDir) { done, total ->
                    runOnUiThread {
                        statusText.text = if (total > 0)
                            "Downloading rootfs: ${done * 100 / total}%"
                        else "Downloading rootfs: ${done / 1024 / 1024} MB"
                    }
                }
                runOnUiThread {
                    statusText.text = getString(R.string.status_rootfs_ready)
                    refreshRootfsUi()
                }
            } catch (e: Exception) {
                Log.e(TAG, "rootfs download failed", e)
                runOnUiThread {
                    statusText.text = "Download failed: ${e.message}"
                    refreshRootfsUi()
                }
            }
        }
    }

    private fun setRootfsButtonsEnabled(enabled: Boolean) {
        pickRootfsButton.isEnabled = enabled
        downloadRootfsButton.isEnabled = enabled
        startStopButton.isEnabled = enabled && RootfsManager.targetFile(assetsDir).exists()
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return null
    }

    private fun startBoot() {
        startStopButton.isEnabled = false
        setRootfsButtonsEnabled(false)
        statusText.text = getString(R.string.status_preparing)

        thread {
            try {
                val files = BundledAssets.ensureAll(assets, assetsDir)
                val kernel = files.getValue(BundledAssets.KERNEL.outName)
                val stub = files.getValue(BundledAssets.STUB_EXE.outName)
                val initrd = files.getValue(BundledAssets.INITRD.outName)
                val rootfs = RootfsManager.targetFile(assetsDir)
                if (!rootfs.exists()) {
                    runOnUiThread {
                        statusText.text = getString(R.string.status_missing_rootfs)
                        refreshRootfsUi()
                    }
                    return@thread
                }

                val home = File(cacheDir, "umhome").apply { mkdirs() }
                val args = BootConfig.args(kernel, stub, rootfs, initrd, home)
                val env = arrayOf(
                    "HOME=${home.absolutePath}",
                    "TMPDIR=${cacheDir.absolutePath}",
                    "PATH=/system/bin"
                )

                runOnUiThread {
                    statusText.text = getString(R.string.status_booting)
                    bootSession(kernel.absolutePath, args.drop(1).toTypedArray(), env, home)
                    startStopButton.text = getString(R.string.stop)
                    startStopButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "boot failed", e)
                runOnUiThread {
                    statusText.text = "Failed: ${e.message}"
                    refreshRootfsUi()
                }
            }
        }
    }

    private fun bootSession(kernelPath: String, args: Array<String>, env: Array<String>, cwd: File) {
        val client = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView.onScreenUpdated()
            }
            override fun onTitleChanged(changedSession: TerminalSession) {}
            override fun onSessionFinished(finishedSession: TerminalSession) {
                runOnUiThread {
                    session = null
                    startStopButton.text = getString(R.string.start)
                    refreshRootfsUi()
                }
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
            override fun getTerminalCursorStyle(): Int? = null
            override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
            override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
            override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
            override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }

        // TerminalSession forks kernelPath with a real pty attached to fd 0/1/2 -
        // that's what lets the UML kernel's console reach this on-screen terminal
        // with no extra con0= plumbing.
        val newSession = TerminalSession(kernelPath, cwd.absolutePath, args, env, 2000, client)
        session = newSession
        terminalView.attachSession(newSession)
        terminalView.requestFocus()
        statusText.text = getString(R.string.status_running)
        pickRootfsButton.isEnabled = false
        downloadRootfsButton.isEnabled = false
    }

    private fun stopBoot() {
        session?.finishIfRunning()
        session = null
        startStopButton.text = getString(R.string.start)
        refreshRootfsUi()
    }
}
