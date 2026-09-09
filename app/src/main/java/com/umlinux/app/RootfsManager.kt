package com.umlinux.app

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Gets a rootfs into place when it isn't bundled in the APK
 * (BundledAssets.tryExtractRootfs returns null): either the user picks an
 * existing file with the system file manager (SAF), or the app downloads
 * one over HTTP.
 *
 * Both write to BundledAssets.targetPath - the exact path
 * tryExtractRootfs() checks for on the "does the file already exist"
 * fast-path - so once either succeeds here, a normal boot attempt picks it
 * up next time with no extra wiring.
 */
object RootfsManager {

    // The release actually exercised in testing this app's boot command
    // (the mem=/ubd0=/initrd=/stub_exe= line in BootConfig). Swap this for
    // a purpose-built rootfs URL once one exists, or drop the download
    // button entirely if only file-picker import is wanted.
    const val DEFAULT_DOWNLOAD_URL =
        "https://github.com/zalexdev/strykerapp/releases/download/rootless-650/rootfs.imgz"

    fun interface Progress {
        fun onProgress(bytesDone: Long, bytesTotal: Long)
    }

    fun targetFile(assetsDir: File): File = File(assetsDir, BundledAssets.ROOTFS.outName)

    /**
     * Copies a user-picked SAF [uri] into place, transparently decompressing
     * it if [displayName] ends in .gz/.imgz (mirrors AssetDownloader's
     * handling of the equivalent .gz release asset).
     */
    fun copyFromUri(resolver: ContentResolver, uri: Uri, assetsDir: File, displayName: String?): File {
        assetsDir.mkdirs()
        val target = targetFile(assetsDir)
        val tmp = File(assetsDir, target.name + ".part")
        val looksGzipped = displayName?.let { it.endsWith(".gz") || it.endsWith(".imgz") } ?: false

        val raw = resolver.openInputStream(uri) ?: throw IOException("Could not open picked file")
        raw.use { input ->
            val source: InputStream = if (looksGzipped) GZIPInputStream(input) else input
            tmp.outputStream().use { out -> source.copyTo(out) }
        }
        if (!tmp.renameTo(target)) throw IOException("Could not move ${tmp.name} into place")
        return target
    }

    /** Downloads [url] into place, decompressing on the fly if the URL ends in .gz/.imgz. */
    fun download(url: String, assetsDir: File, progress: Progress? = null): File {
        assetsDir.mkdirs()
        val target = targetFile(assetsDir)
        val tmp = File(assetsDir, target.name + ".part")
        val looksGzipped = url.endsWith(".gz") || url.endsWith(".imgz")

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw IOException("HTTP ${conn.responseCode} fetching $url")
        }
        val total = conn.contentLengthLong
        var done = 0L

        conn.inputStream.use { raw ->
            // Counts compressed bytes as they come off the socket, so the
            // progress percentage stays accurate against Content-Length even
            // though the decompressed rootfs is a different (larger) size.
            val counting = object : FilterInputStream(raw) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) {
                        done += n
                        progress?.onProgress(done, total)
                    }
                    return n
                }
            }
            val source: InputStream = if (looksGzipped) GZIPInputStream(counting) else counting
            FileOutputStream(tmp).use { out -> source.copyTo(out) }
        }
        if (!tmp.renameTo(target)) throw IOException("Could not move ${tmp.name} into place")
        return target
    }
}
