package com.umlinux.app

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

private const val TAG = "AssetDownloader"

class AssetDownloader(private val destDir: File) {

    fun interface Progress {
        fun onProgress(fileName: String, bytesDone: Long, bytesTotal: Long)
    }

    /** Downloads every asset that isn't already present+verified. Returns the final file paths. */
    fun ensureAll(progress: Progress? = null): Map<String, File> {
        destDir.mkdirs()
        val result = mutableMapOf<String, File>()
        for (asset in ReleaseAssets.ALL) {
            result[asset.fileName] = ensureOne(asset, progress)
        }
        return result
    }

    private fun ensureOne(asset: ReleaseAssets.Asset, progress: Progress?): File {
        val target = File(destDir, asset.fileName)
        if (target.exists() && verify(target, asset.sha256)) {
            Log.i(TAG, "${asset.fileName} already present, skipping download")
            return target
        }

        val tmp = File(destDir, asset.fileName + ".part")
        val conn = URL(asset.url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw java.io.IOException("HTTP ${conn.responseCode} fetching ${asset.url}")
        }
        val total = conn.contentLengthLong
        var done = 0L
        conn.inputStream.use { input ->
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    progress?.onProgress(asset.fileName, done, total)
                }
            }
        }

        if (!verify(tmp, asset.sha256)) {
            if (asset.sha256 == null) {
                Log.w(TAG, "No sha256 pinned for ${asset.fileName} - downloaded without verification. " +
                        "Fill in ReleaseAssets before shipping a real build.")
            } else {
                tmp.delete()
                throw java.io.IOException("Checksum mismatch for ${asset.fileName}")
            }
        }
        tmp.renameTo(target)

        // The .gz rootfs image needs to be expanded before UML's ubd driver can use it.
        if (target.name.endsWith(".gz")) {
            return gunzip(target)
        }
        target.setExecutable(true)
        return target
    }

    private fun gunzip(gz: File): File {
        val out = File(gz.parentFile, gz.name.removeSuffix(".gz"))
        if (out.exists()) return out
        GZIPInputStream(gz.inputStream()).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun verify(file: File, expectedSha256: String?): Boolean {
        if (expectedSha256 == null) return file.exists() && file.length() > 0
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }
}
