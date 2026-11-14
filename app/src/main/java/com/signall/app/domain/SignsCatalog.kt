package com.signall.app.domain

/**
 * Signs shipped inside the APK, from `tools/pack_avatars.py`.
 *
 * One entry per sign of LSC50 that has an exported animation (rep 0, angle 0);
 * each is a clip named `<id>` inside `assets/web/models/juan.glb` or `assets/web/models/pauline.glb`,
 * the files that also hold the character.
 *
 * This file is GENERATED — `tools/pack_avatars.py` rewrites it from the assets it
 * just wrote, so the list and the assets cannot drift apart. Editing it
 * by hand will be undone on the next generator run.
 *
 * The character itself is NOT per sign: the scene loads one of
 * `assets/web/models/juan.glb` or `assets/web/models/pauline.glb` once and every sign is a clip retargeted to that same
 * skeleton. Replacing the avatar therefore needs no re-export of the signs.
 *
 * All 50 vocabulary signs of LSC50 are here, bundled in the
 * avatar. Signs outside this list fall back to [DEFAULT] rather than
 * failing (RF-CAT-03).
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
        "0032_0000_0000", "0033_0000_0000", "0034_0000_0000", "0035_0000_0000",
        "0036_0000_0000", "0037_0000_0000", "0038_0000_0000", "0039_0000_0000",
        "0040_0000_0000", "0041_0000_0000", "0042_0000_0000", "0043_0000_0000",
        "0044_0000_0000", "0045_0000_0000", "0046_0000_0000", "0047_0000_0000",
        "0048_0000_0000", "0049_0000_0000",
    )

    /** The sign the app opens on, and the one it falls back to. */
    const val DEFAULT: String = "0000_0000_0000"

    /**
     * Prefix of the asset contract: every sign is `signs/<id>.glb`.
     *
     * It is the same string the backend's catalog answers, so it is an
     * identifier, not a label: it must not change.
     */
    private const val SIGN_PATH_PREFIX = "signs/"

    /**
     * The logical path of a sign, as a same-origin URL.
     *
     * The animation itself is a clip inside `assets/web/models/juan.glb` or `assets/web/models/pauline.glb`, and the scene
     * resolves this path to that clip **by name** without any request: it parses
     * `<id>` out of the path and looks it up among the clips it already loaded
     * (see `embeddedClipsFor` in `app.js`). The path is kept as the identifier
     * because it is also what the backend's catalog answers, so the same string
     * means the same sign on both sides.
     */
    fun assetUrl(id: String): String = "$SIGN_PATH_PREFIX$id.glb"

    /**
     * Resolves a `glb_animation_path` coming from the backend to a local asset,
     * or null when that path is not one of ours.
     *
     * The backend answers with the path we seeded in `catalog`, and since the
     * animation ships inside the avatar the scene resolves it by name, without
     * touching the network. A path that matches a sign in [available] is
     * accepted; anything else is refused and the caller treats it as "download
     * it instead".
     */
    fun localAssetOrNull(path: String): String? {
        val clean = path.trim()
        if (!clean.startsWith(SIGN_PATH_PREFIX) || clean.contains("..") || clean.contains("//")) return null
        if (!clean.endsWith(".glb")) return null
        val id = clean.removePrefix(SIGN_PATH_PREFIX).removeSuffix(".glb")
        return if (id in available) clean else null
    }

    /**
     * True when [path] is one of *our* asset paths, whether or not we ship it.
     *
     * The counterpart of [localAssetOrNull]: that one says "we have it", this
     * one says "nobody is going to serve it over HTTP either". Every LSC50 gloss
     * has its clip bundled, so a `signs/<id>.glb` in our layout is never a
     * remote asset: asking the backend for one would be a guaranteed 404. A
     * path that is *not* in this layout is a real remote asset and still goes
     * down the download path.
     */
    fun isOwnAssetPath(path: String): Boolean {
        val clean = path.trim()
        return clean.startsWith(SIGN_PATH_PREFIX) && clean.endsWith(".glb") &&
            !clean.contains("..") && !clean.contains("//")
    }
}
