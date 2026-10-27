package io.github.kalasyan.recompa.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The catalog as published in `index.json` by `tools/catalog/build_index.py`. */
@Serializable
data class CatalogIndex(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("generated_at") val generatedAt: String,
    val ports: List<Port>,
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
) {
    /** The first fork with a published release; forks are listed in order of preference. */
    val recommendedFork: Fork? get() = forks.firstOrNull { it.release != null }
}

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
)

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
)

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
