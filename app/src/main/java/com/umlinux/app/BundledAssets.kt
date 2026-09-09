package com.umlinux.app

import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/**
 * The UML kernel binary and stub_exe are compiled locally (not pulled from
 * zalexdev's GitHub release - see ReleaseAssets/AssetDownloader, now unused
 * but kept for reference) and shipped inside the APK under assets/uml/.
 *
 * Assets can't be exec()'d in place, so this copies each one out to internal
 * storage on first run and marks the binaries executable. Re-extraction is
 * skipped once a file of the same name already exists in destDir - these are
 * static build outputs, not something that changes between launches. If you
 * ship a new build, bump the version name/code (or just clear app storage)
 * so the new binary gets copied out again.
 */
object BundledAssets {
    private const val ASSET_DIR = "uml"

    data class Entry(val assetName: String, val outName: String, val executable: Boolean)

    val KERNEL = Entry("linux-uml.bin", "linux-uml", executable = true)
    val STUB_EXE = Entry("stub_exe.bin", "stub_exe", executable = true)
    val INITRD = Entry("initrd.img.bin", "initrd.img", executable = false)

    // Always bundled - boot can't proceed without these.
    val REQUIRED = listOf(KERNEL, STUB_EXE, INITRD)

    // Not bundled yet - Danish said the rootfs is coming in a follow-up.
    // Once it lands: drop it at app/src/main/assets/uml/rootfs.bin and it
    // will be picked up automatically by tryExtractRootfs() below - no other
    // code changes needed. Rename here if the real filename differs.
    val ROOTFS = Entry("rootfs.bin", "rootfs.img", executable = false)

    class MissingAssetException(val entry: Entry) :
        IOException("Bundled asset missing: $ASSET_DIR/${entry.assetName}")

    /** Copies every required bundled asset into [destDir]. Throws if one isn't in the APK. */
    fun ensureAll(assets: AssetManager, destDir: File): Map<String, File> {
        destDir.mkdirs()
        return REQUIRED.associate { it.outName to extract(assets, it, destDir) }
    }

    /** Like [extract], but returns null instead of throwing when the asset isn't present yet. */
    fun tryExtractRootfs(assets: AssetManager, destDir: File): File? =
        try {
            extract(assets, ROOTFS, destDir)
        } catch (e: MissingAssetException) {
            null
        }

    private fun extract(assets: AssetManager, entry: Entry, destDir: File): File {
        val target = File(destDir, entry.outName)
        val assetPath = "$ASSET_DIR/${entry.assetName}"

        if (!target.exists()) {
            val tmp = File(destDir, entry.outName + ".part")
            try {
                assets.open(assetPath).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: IOException) {
                tmp.delete()
                throw MissingAssetException(entry)
            }
            tmp.renameTo(target)
        }
        if (entry.executable) target.setExecutable(true)
        return target
    }
}
