package io.github.kalasyan.recompa.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The catalog as published in `index.json` by `tools/catalog/build_index.py`. */
@Serializable
data class CatalogIndex(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("generated_at") val generatedAt: String,
    val ports: List<Port>,
    /** Where the app sends reports and suggestions without a GitHub account; null opens the GitHub forms. */
    @SerialName("feedback_url") val feedbackUrl: String? = null,
    /** The original platforms with their names, so the app can show ones added after it was built. */
    val platforms: List<Platform> = emptyList(),
)

@Serializable
data class Platform(val id: String, val name: String)

@Serializable
data class Port(
    val id: String,
    val name: String,
    val game: String,
    /** What to sort the game by when its name doesn't (say "Zelda" for "The Legend of Zelda"); null to use [game]. */
    @SerialName("sort_name") val sortName: String? = null,
    @SerialName("original_platform") val originalPlatform: String,
    val type: String,
    val upstream: String? = null,
    val license: String? = null,
    val description: String? = null,
    val forks: List<Fork>,
    val requirements: Requirements,
    val assets: List<Asset> = emptyList(),
    val media: Media? = null,
    val art: Art? = null,
    /** How the port runs on each device, from players' reports of all its versions. */
    val reports: List<DeviceReport> = emptyList(),
    /** The texts in other languages, by language code. The fields above are in English. */
    val i18n: Map<String, PortTexts> = emptyMap(),
    /** When the catalog first published this port (UTC, ISO 8601); null in catalogs older than the field. */
    @SerialName("added_at") val addedAt: String? = null,
    /** Ids of ports merged into this one: what Recompa installed under them belongs here now. */
    val aliases: List<String> = emptyList(),
    /** The game's genres, ids from [Genres.ALL]; empty in catalogs older than the field. */
    val genres: List<String> = emptyList(),
    /** How many times the APKs of all its versions were downloaded from GitHub; null in older catalogs. */
    val downloads: Long? = null,
) {
    /**
     * The versions of the game a player can install: each fork (or separate port of the same
     * game) with a published release.
     */
    val versions: List<Fork> get() = forks.filter { it.release != null }

    /** The version to suggest: the best rated by players, else the first one (see [ForkRanking]). */
    val recommendedFork: Fork? get() = ForkRanking.best(forks)

    fun fork(repo: String?): Fork? = forks.firstOrNull { it.repo == repo }

    /** This port with its texts in [language] when the catalog has them, in English otherwise. */
    fun localized(language: String): Port {
        val texts = i18n[language] ?: return this
        return copy(
            description = texts.description ?: description,
            assets = assets.map { asset -> texts.assets[asset.id]?.let(asset::localized) ?: asset },
        )
    }
}

@Serializable
data class PortTexts(
    val description: String? = null,
    val assets: Map<String, AssetTexts> = emptyMap(),
)

@Serializable
data class AssetTexts(
    val label: String? = null,
    @SerialName("instructions_md") val instructionsMd: String? = null,
    /** Labels of the accepted files that have one, in the same order. */
    val accepted: List<String> = emptyList(),
)

/**
 * One version of a port's game: a build from one repository. Several forks of the same project,
 * and separate ports of the same game, are all versions of one port; they share everything but
 * their releases and their players' reports.
 */
@Serializable
data class Fork(
    val repo: String,
    /** This version's own name, when it is not the port's. */
    val name: String? = null,
    /** This version's type (recomp, decomp...), when it is not the port's. */
    val type: String? = null,
    /** The id of the port this version was in before it was merged into this one. */
    val formerly: String? = null,
    val official: Boolean = false,
    val channel: String = "stable",
    @SerialName("asset_pattern") val assetPattern: String,
    @SerialName("package") val packageName: String? = null,
    @SerialName("signer_sha256") val signerSha256: String? = null,
    val verified: Boolean = false,
    @SerialName("tested_on") val testedOn: List<String> = emptyList(),
    val release: Release? = null,
    /** Earlier releases that can still be installed, newest first. */
    val history: List<Release> = emptyList(),
    /** Releases the catalog warns against, for example because they break saves. */
    val blocked: List<BlockedRelease> = emptyList(),
    /** How this version runs on each device, from players' reports. */
    val reports: List<DeviceReport> = emptyList(),
    /** How many times this version's APKs were downloaded from GitHub; null in older catalogs. */
    val downloads: Long? = null,
) {
    fun blockedReason(tag: String?): BlockedRelease? = blocked.firstOrNull { it.tag == tag }

    /** The repository's name without its owner, to tell versions apart when they have no name. */
    val repoName: String get() = repo.substringAfter('/')
}

/** How players rate the versions of a port, to recommend one. */
object ForkRanking {
    /** Positive reports (perfect or playable) minus broken ones, on every device. */
    fun score(reports: List<DeviceReport>): Int = reports.sumOf { it.great + it.playable - it.broken }

    /**
     * The best rated fork with a release. Ties (and forks nobody reported on yet) go to the
     * most "perfect" reports, then to the catalog's order, which already puts the best rated
     * first when it was built and otherwise follows the order the curator chose.
     */
    fun best(forks: List<Fork>): Fork? = forks.withIndex()
        .filter { it.value.release != null }
        .maxWithOrNull(
            compareBy<IndexedValue<Fork>>({ score(it.value.reports) }, { r -> r.value.reports.sumOf { it.great } })
                .thenByDescending { it.index },
        )?.value
}

@Serializable
data class BlockedRelease(
    val tag: String,
    val reason: String? = null,
)

/** Players' reports for one device model. */
@Serializable
data class DeviceReport(
    val device: String,
    val great: Int = 0,
    val playable: Int = 0,
    val broken: Int = 0,
    /** The release tag of the newest report, when the player gave one. */
    @SerialName("last_tag") val lastTag: String? = null,
    @SerialName("last_at") val lastAt: String? = null,
) {
    val total: Int get() = great + playable + broken
}

@Serializable
data class Release(
    val tag: String,
    val name: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val asset: ReleaseAsset,
)

@Serializable
data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long? = null,
    val sha256: String? = null,
    /** Set when the asset is a ZIP: the APK inside it that Recompa installs. */
    val entry: ArchiveEntry? = null,
)

@Serializable
data class ArchiveEntry(
    val path: String,
    val size: Long? = null,
    val sha256: String,
)

@Serializable
data class Requirements(
    val abi: List<String>,
    @SerialName("min_sdk") val minSdk: Int,
    val vulkan: Boolean = false,
)

@Serializable
data class Asset(
    val id: String,
    val label: String,
    val accepted: List<AcceptedFile> = emptyList(),
    @SerialName("convert_from") val convertFrom: List<String> = emptyList(),
    @SerialName("zip_ok") val zipOk: Boolean = true,
    val delivery: Delivery,
    @SerialName("instructions_md") val instructionsMd: String? = null,
) {
    fun localized(texts: AssetTexts): Asset {
        val labels = texts.accepted.iterator()
        return copy(
            label = texts.label ?: label,
            instructionsMd = texts.instructionsMd ?: instructionsMd,
            accepted = accepted.map { file ->
                if (file.label != null && labels.hasNext()) file.copy(label = labels.next()) else file
            },
        )
    }
}

@Serializable
data class AcceptedFile(
    val sha1: String,
    val label: String? = null,
    val format: String? = null,
)

@Serializable
data class Delivery(
    val mode: DeliveryMode,
    val path: String? = null,
    val filename: String? = null,
)

@Serializable
enum class DeliveryMode {
    @SerialName("shared_folder") SHARED_FOLDER,
    @SerialName("in_app_picker") IN_APP_PICKER,
    @SerialName("intent") INTENT,
}

@Serializable
data class Media(
    val icon: String? = null,
    val screenshots: List<String> = emptyList(),
)

/** Art resolved by the catalog build: SteamGridDB cover, hero and logo, and IGDB screenshots. */
@Serializable
data class Art(
    val cover: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val screenshots: List<String> = emptyList(),
    @SerialName("igdb_cover") val igdbCover: String? = null,
) {
    /** Covers to try in order: SteamGridDB's, then IGDB's. */
    val covers: List<String> get() = listOfNotNull(cover, igdbCover)

    /** Wide backgrounds to try in order: the hero, then the first screenshot. */
    val backdrops: List<String> get() = listOfNotNull(hero, screenshots.firstOrNull())
}

/** The genres a port can have, in the order the library lists them (the catalog's build uses the same ids). */
object Genres {
    val ALL = listOf(
        "action", "adventure", "platform", "shooter", "rpg", "fighting", "racing", "sports",
        "strategy", "simulation", "puzzle", "horror", "arcade", "music",
    )

    /** The genres of [ports] with how many ports each has, in the order of [ALL]; unknown ids last. */
    fun counts(ports: List<Port>): List<Pair<String, Int>> = ports.flatMap { it.genres.distinct() }
        .groupingBy { it }.eachCount().entries
        .sortedBy { ALL.indexOf(it.key).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        .map { it.key to it.value }
}
