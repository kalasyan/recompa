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
)

@Serializable
data class Port(
    val id: String,
    val name: String,
    val game: String,
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
    /** How the port runs on each device, from players' reports. */
    val reports: List<DeviceReport> = emptyList(),
    /** The texts in other languages, by language code. The fields above are in English. */
    val i18n: Map<String, PortTexts> = emptyMap(),
) {
    /** The first fork with a published release; forks are listed in order of preference. */
    val recommendedFork: Fork? get() = forks.firstOrNull { it.release != null }

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

@Serializable
data class Fork(
    val repo: String,
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
) {
    fun blockedReason(tag: String?): BlockedRelease? = blocked.firstOrNull { it.tag == tag }
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

/** Cover art resolved by the catalog build (usually from SteamGridDB). */
@Serializable
data class Art(
    val cover: String? = null,
    val hero: String? = null,
    val logo: String? = null,
)
