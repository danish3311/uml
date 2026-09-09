package com.umlinux.app

import java.io.File

/**
 * Kernel command line for booting.
 *
 * This mirrors the command line that's actually been run and confirmed to
 * get through UML's early startup checks on real ARM64 hardware:
 *
 *   ./linux-uml mem=1024M ubd0="$PWD/rootfs.img" root=/dev/ubda \
 *       initrd="$PWD/initrd.img" stub_exe="$PWD/stub_exe" panic=-1 \
 *       con=null con0=fd:0,fd:1
 *
 * Two deltas from that raw command, both deliberate:
 *  - `rw` is added so the root device isn't mounted read-only. Harmless to
 *    drop if that turns out to matter for how rootfs.img wants to be mounted.
 *  - `con0=fd:0,fd:1` is kept explicit rather than relying on defaults, even
 *    though MainActivity's TerminalSession fork already attaches a pty to
 *    fd 0/1/2 - it's what was actually exercised in testing, so it stays
 *    pinned rather than assumed equivalent.
 */
object BootConfig {
    const val memory = "1024M"
    const val ubdRootDevice = "/dev/ubda"
    const val initPath = "" // leave blank to use the rootfs's own /sbin/init

    fun args(kernel: File, stubExe: File, rootfs: File, initrd: File, home: File): List<String> {
        val a = mutableListOf(
            kernel.absolutePath,
            "mem=$memory",
            "ubd0=${rootfs.absolutePath}",
            "root=$ubdRootDevice",
            "initrd=${initrd.absolutePath}",
            "rw",
            "stub_exe=${stubExe.absolutePath}",
            "panic=-1",
            "con=null",
            "con0=fd:0,fd:1",
        )
        if (initPath.isNotBlank()) {
            a += "init=$initPath"
        }
        return a
    }
}
