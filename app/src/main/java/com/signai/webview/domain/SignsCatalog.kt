package com.signai.webview.domain

/**
 * Signs shipped inside the APK, from `tools/split_signs.py`.
 *
 * One entry per sign of LSC50 that has an exported animation (rep 0, angle 0);
 * each maps to `assets/web/signs/<id>.glb`.
 *
 * This file is GENERATED — `tools/split_signs.py` rewrites it from the assets it
 * just wrote, so the list and the files on disk cannot drift apart. Editing it
 * by hand will be undone on the next generator run.
 *
 * The character itself is NOT per sign: `assets/web/models/avatar.glb` is loaded
 * once and every sign is an animation-only clip retargeted to that same
 * skeleton. Replacing the avatar therefore needs no re-export of the signs.
 *
 * Only 32 of the 50 vocabulary signs are here — 0032–0049 still have no export. Signs outside
 * this list fall back to [DEFAULT] rather than failing (RF-CAT-03).
 *
 * These are *animation* assets, not glosses. What a sign *means* still comes
 * from the backend's catalog, which holds all 50 LSC50 glosses: `available` on
 * a translation is about the gloss, not about whether we have the movement. A
 * gloss whose animation is not in this list is announced anyway, and
 * [localAssetOrNull] returns null for it so the caller falls back without
 * asking the backend for a file nobody serves.
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
    )

    /** The sign the app opens on, and the one it falls back to. */
    const val DEFAULT: String = "0000_0000_0000"

    /** Where a sign's animation lives, as a same-origin asset path. */
    fun assetUrl(id: String): String = "signs/$id.glb"

    /**
     * Resolves a `glb_animation_path` coming from the backend to a local asset,
     * or null when that path is not one of ours.
     *
     * The backend answers with the path we seeded in `catalog`, and since the
     * assets ship inside the APK the path is already same-origin: the scene
     * loads it directly. Only a path that matches a sign we actually ship is
     * accepted, so a sign whose animation is not exported yet (0032–0049)
     * returns null and the caller falls back instead of requesting a file that
     * does not exist.
     *
     * Anything else — a remote URL, a traversal, a name we do not ship — is
     * refused, and the caller treats it as "download it instead".
     */
    fun localAssetOrNull(path: String): String? {
        val clean = path.trim()
        if (!clean.startsWith("signs/") || clean.contains("..") || clean.contains("//")) return null
        if (!clean.endsWith(".glb")) return null
        val id = clean.removePrefix("signs/").removeSuffix(".glb")
        return if (id in available) clean else null
    }

    /**
     * True when [path] is one of *our* asset paths, whether or not we ship it.
     *
     * The counterpart of [localAssetOrNull]: that one says "we have it", this
     * one says "nobody is going to serve it over HTTP either". The catalog
     * announces all 50 LSC50 glosses but only 32 have an export, so the
     * rest come back with a `signs/<id>.glb` we do not ship. Asking the backend
     * for those is a guaranteed 404 that costs a round trip on every utterance
     * and changes nothing, so the caller skips straight to the fallback. A path
     * that is *not* in this layout is a real remote asset and still goes down
     * the download path.
     */
    fun isOwnAssetPath(path: String): Boolean {
        val clean = path.trim()
        return clean.startsWith("signs/") && clean.endsWith(".glb") &&
            !clean.contains("..") && !clean.contains("//")
    }
}
