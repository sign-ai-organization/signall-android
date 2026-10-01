package com.signai.webview.domain

/**
 * Signs baked into the APK, from `tools/build_signs.py`.
 *
 * One entry per vocabulary sign of LSC50 (rep 0, angle 0); each maps to
 * `assets/web/signs/<id>.json`.
 *
 * This file is GENERATED — `tools/build_signs.py` rewrites it from the assets
 * it just wrote, so the list and the files on disk cannot drift apart. Editing
 * it by hand will be undone on the next generator run.
 *
 * These are *animation* assets, not glosses. What a sign *means* still comes
 * from the backend's catalog, which is still empty: `available` on a
 * translation is about the gloss, not about whether we have the movement.
 */
object SignsCatalog {
    val available: List<String> = listOf(
        "0000_0000_0000", "0001_0000_0000", "0002_0000_0000", "0003_0000_0000",
        "0004_0000_0000", "0005_0000_0000", "0006_0000_0000", "0007_0000_0000",
        "0008_0000_0000", "0009_0000_0000", "0010_0000_0000", "0011_0000_0000",
        "0012_0000_0000", "0013_0000_0000", "0014_0000_0000", "0015_0000_0000",
        "0016_0000_0000", "0017_0000_0000", "0018_0000_0000", "0019_0000_0000",
        "0020_0000_0000", "0021_0000_0000", "0022_0000_0000", "0023_0000_0000",
        "0024_0000_0000", "0025_0000_0000", "0026_0000_0000", "0027_0000_0000",
        "0028_0000_0000", "0029_0000_0000", "0030_0000_0000", "0031_0000_0000",
        "0032_0000_0000", "0033_0000_0000", "0034_0000_0000", "0035_0000_0000",
        "0036_0000_0000", "0037_0000_0000", "0038_0000_0000", "0039_0000_0000",
        "0040_0000_0000", "0041_0000_0000", "0042_0000_0000", "0043_0000_0000",
        "0044_0000_0000", "0045_0000_0000", "0046_0000_0000", "0047_0000_0000",
        "0048_0000_0000", "0049_0000_0000",
    )

    /** The sign the app opens on, and the one it falls back to. */
    const val DEFAULT: String = "0000_0000_0000"

    /** Where a sign's animation lives, as a same-origin asset path. */
    fun assetUrl(id: String): String = "signs/$id.json"
}
