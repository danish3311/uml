package com.umlinux.app

/**
 * UNUSED as of the switch to locally-compiled binaries - MainActivity now
 * gets the kernel/stub_exe/rootfs from BundledAssets (assets/uml/ inside
 * the APK) instead of downloading them. Left in place as a fallback: if you
 * ever want to pull from zalexdev's public prebuilt release again instead of
 * a local build, wire AssetDownloader back into MainActivity.startBoot().
 *
 * Points only at the PUBLIC zalexdev/linux-um-arm64 release.
 * This is a from-scratch app: it does not use Stryker's manifest, chroot
 * assets, or release channel. If zalexdev cuts a newer prebuilt tag with
 * updated networking support, bump RELEASE_TAG here.
 */
object ReleaseAssets {
    private const val REPO = "zalexdev/linux-um-arm64"
    const val RELEASE_TAG = "prebuilt-20260816"

    private fun assetUrl(name: String) =
        "https://github.com/$REPO/releases/download/$RELEASE_TAG/$name"

    data class Asset(val fileName: String, val url: String, val sha256: String?)

    // sha256 values are intentionally left null here - they were not
    // available to hardcode automatically. Before shipping, pull the real
    // digests from the release page (or a checksums.txt asset, if the
    // maintainer publishes one) and fill these in; AssetDownloader will
    // verify against them when present and just log a warning when absent.
    val KERNEL = Asset("linux", assetUrl("linux"), sha256 = null)
    val STUB_EXE = Asset("stub_exe", assetUrl("stub_exe"), sha256 = null)
    val ROOTFS_DOCKER = Asset("debian-docker.ext4.gz", assetUrl("debian-docker.ext4.gz"), sha256 = null)

    val ALL = listOf(KERNEL, STUB_EXE, ROOTFS_DOCKER)
}
