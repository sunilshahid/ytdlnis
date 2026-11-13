package com.deniscerri.ytdl.vault

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.play.device.DeviceSort
import com.deniscerri.ytdl.util.SortCategory
import com.deniscerri.ytdl.util.SortOrderLabels
import com.deniscerri.ytdl.vault.crypto.VaultCryptoPrimitives
import com.deniscerri.ytdl.vault.crypto.VaultHeader
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.json.JSONObject

/** Encrypted sidecar payload (`{uuid}.meta.enc`). Never written in plaintext outside RAM. */
data class VaultItemMeta(
    val id: String,
    val title: String,
    val originalFileName: String,
    val durationMs: Long,
    val filesizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateAddedMs: Long,
    val sourceUrl: String = "",
    val notes: String = "",
    val cipherVersion: Int = VaultHeader.VERSION_GCM_CHUNKS,
    val originalFilePath: String = "",
    val originalDir: String = "",
    val fromDownloadHistory: Boolean = false,
    val author: String = "",
    val website: String = "",
    val thumbUrl: String = "",
    val durationText: String = "",
    val formatJson: String = "",
    val historyTimeSec: Long = 0L,
    val downloadId: Long = 0L
) {
    fun toJsonString(): String = JsonObject().apply {
        addProperty("id", id)
        addProperty("title", title)
        addProperty("originalFileName", originalFileName)
        addProperty("durationMs", durationMs)
        addProperty("filesizeBytes", filesizeBytes)
        addProperty("width", width)
        addProperty("height", height)
        addProperty("mimeType", mimeType)
        addProperty("dateAddedMs", dateAddedMs)
        addProperty("sourceUrl", sourceUrl)
        addProperty("notes", notes)
        addProperty("cipherVersion", cipherVersion)
        addProperty("originalFilePath", originalFilePath)
        addProperty("originalDir", originalDir)
        addProperty("fromDownloadHistory", fromDownloadHistory)
        addProperty("author", author)
        addProperty("website", website)
        addProperty("thumbUrl", thumbUrl)
        addProperty("durationText", durationText)
        addProperty("formatJson", formatJson)
        addProperty("historyTimeSec", historyTimeSec)
        addProperty("downloadId", downloadId)
    }.toString()

    companion object {
        private fun JsonObject.str(key: String, default: String = ""): String =
            get(key)?.takeIf { !it.isJsonNull }?.asString ?: default

        private fun JsonObject.lng(key: String, default: Long = 0L): Long =
            get(key)?.takeIf { !it.isJsonNull }?.asLong ?: default

        private fun JsonObject.intVal(key: String, default: Int = 0): Int =
            get(key)?.takeIf { !it.isJsonNull }?.asInt ?: default

        private fun JsonObject.boolVal(key: String, default: Boolean = false): Boolean =
            get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: default

        fun fromJsonString(raw: String): VaultItemMeta {
            val o = JsonParser.parseString(raw).asJsonObject
            return VaultItemMeta(
                id = o.get("id").asString,
                title = o.str("title", "Untitled"),
                originalFileName = o.str("originalFileName", ""),
                durationMs = o.lng("durationMs", 0L),
                filesizeBytes = o.lng("filesizeBytes", 0L),
                width = o.intVal("width", 0),
                height = o.intVal("height", 0),
                mimeType = o.str("mimeType", "video/mp4"),
                dateAddedMs = o.lng("dateAddedMs", System.currentTimeMillis()),
                sourceUrl = o.str("sourceUrl", ""),
                notes = o.str("notes", ""),
                cipherVersion = o.intVal("cipherVersion", VaultHeader.VERSION_GCM_CHUNKS),
                originalFilePath = o.str("originalFilePath", ""),
                originalDir = o.str("originalDir", ""),
                fromDownloadHistory = o.boolVal("fromDownloadHistory", false),
                author = o.str("author", ""),
                website = o.str("website", ""),
                thumbUrl = o.str("thumbUrl", ""),
                durationText = o.str("durationText", ""),
                formatJson = o.str("formatJson", ""),
                historyTimeSec = o.lng("historyTimeSec", 0L),
                downloadId = o.lng("downloadId", 0L)
            )
        }
    }
}

/** In-memory gallery presentation item (disposed on lock). */
data class VaultUiItem(
    val meta: VaultItemMeta,
    val thumbnail: Bitmap?
)

enum class VaultLayoutMode(@param:StringRes val labelRes: Int) {
    LIST(R.string.list),
    GRID(R.string.grid)
}

enum class VaultSortBy(@param:StringRes val labelRes: Int, val category: SortCategory) {
    DATE(R.string.device_sort_date, SortCategory.DATE_TIME),
    TITLE(R.string.device_sort_title, SortCategory.ALPHABETICAL),
    LENGTH(R.string.duration, SortCategory.DURATION),
    SIZE(R.string.file_size, SortCategory.SIZE),
    RESOLUTION(R.string.device_sort_resolution, SortCategory.MAGNITUDE),
    AUTHOR(R.string.author, SortCategory.ALPHABETICAL),
    WEBSITE(R.string.vault_sort_website, SortCategory.ALPHABETICAL)
}

enum class VaultField(@param:StringRes val labelRes: Int) {
    RESOLUTION(R.string.device_sort_resolution),
    SIZE(R.string.file_size),
    DATE(R.string.device_sort_date),
    DURATION(R.string.duration),
    AUTHOR(R.string.author),
    WEBSITE(R.string.vault_sort_website)
}

fun compareVaultMeta(
    first: VaultItemMeta,
    second: VaultItemMeta,
    sortBy: VaultSortBy,
    descending: Boolean
): Int {
    val primary = when (sortBy) {
        VaultSortBy.DATE -> first.dateAddedMs.compareTo(second.dateAddedMs)
        VaultSortBy.TITLE -> SortOrderLabels.compareStrings(first.title, second.title)
        VaultSortBy.LENGTH -> first.durationMs.compareTo(second.durationMs)
        VaultSortBy.SIZE -> first.filesizeBytes.compareTo(second.filesizeBytes)
        VaultSortBy.RESOLUTION -> (first.width.toLong() * first.height).compareTo(
            second.width.toLong() * second.height
        )
        VaultSortBy.AUTHOR -> SortOrderLabels.compareStrings(first.author, second.author)
        VaultSortBy.WEBSITE -> SortOrderLabels.compareStrings(first.website, second.website)
    }
    return SortOrderLabels.applyDirection(primary, descending) {
        if (sortBy.category == SortCategory.ALPHABETICAL) {
            second.dateAddedMs.compareTo(first.dateAddedMs)
        } else {
            SortOrderLabels.compareStrings(first.title, second.title)
        }
    }
}

fun sortVaultItems(
    items: List<VaultUiItem>,
    sortBy: VaultSortBy,
    descending: Boolean
): List<VaultUiItem> = items.sortedWith { a, b ->
    compareVaultMeta(a.meta, b.meta, sortBy, descending)
}

enum class VaultPickerSortBy(@param:StringRes val labelRes: Int, val category: SortCategory) {
    DATE(R.string.device_sort_date, SortCategory.DATE_TIME),
    TITLE(R.string.device_sort_title, SortCategory.ALPHABETICAL),
    LENGTH(R.string.duration, SortCategory.DURATION),
    SIZE(R.string.file_size, SortCategory.SIZE),
    RESOLUTION(R.string.device_sort_resolution, SortCategory.MAGNITUDE),
    PATH(R.string.device_sort_path, SortCategory.ALPHABETICAL);

    fun toDeviceSort(): DeviceSort = when (this) {
        DATE -> DeviceSort.DATE
        TITLE -> DeviceSort.TITLE
        LENGTH -> DeviceSort.LENGTH
        SIZE -> DeviceSort.SIZE
        RESOLUTION -> DeviceSort.RESOLUTION
        PATH -> DeviceSort.PATH
    }
}

enum class VaultPickerField(@param:StringRes val labelRes: Int) {
    PATH(R.string.device_sort_path),
    SIZE(R.string.file_size),
    DATE(R.string.device_sort_date),
    DURATION(R.string.duration),
    RESOLUTION(R.string.device_sort_resolution)
}

/** Encrypted user settings stored in `vault_settings.enc`. */
data class VaultSettings(
    val autoWipeThreshold: Int = DEFAULT_AUTO_WIPE_THRESHOLD,
    val stealthMode: Boolean = false,
    val keepOpenOnPlayerBack: Boolean = false,
    val biometricsEnabled: Boolean = false,
    val passwordManagerEnabled: Boolean = false,
    val layoutMode: VaultLayoutMode = VaultLayoutMode.GRID,
    val sortBy: VaultSortBy = VaultSortBy.DATE,
    val sortDescending: Boolean = true,
    val visibleFields: Set<VaultField> = DEFAULT_VISIBLE_FIELDS,
    val pickerLayoutMode: VaultLayoutMode = VaultLayoutMode.GRID,
    val pickerSortBy: VaultPickerSortBy = VaultPickerSortBy.DATE,
    val pickerSortDescending: Boolean = true,
    val pickerVisibleFields: Set<VaultPickerField> = DEFAULT_PICKER_VISIBLE_FIELDS
) {
    fun withResetViewOptions(): VaultSettings = copy(
        layoutMode = VaultLayoutMode.GRID,
        sortBy = VaultSortBy.DATE,
        sortDescending = true,
        visibleFields = DEFAULT_VISIBLE_FIELDS
    )

    fun toJsonString(): String = JsonObject().apply {
        addProperty("autoWipeThreshold", autoWipeThreshold)
        addProperty("stealthMode", stealthMode)
        addProperty("keepOpenOnPlayerBack", keepOpenOnPlayerBack)
        addProperty("biometricsEnabled", biometricsEnabled)
        addProperty("passwordManagerEnabled", passwordManagerEnabled)
        addProperty("layoutMode", layoutMode.name)
        addProperty("sortBy", sortBy.name)
        addProperty("sortDescending", sortDescending)
        val arr = JsonArray()
        visibleFields.forEach { arr.add(it.name) }
        add("visibleFields", arr)
        addProperty("pickerLayoutMode", pickerLayoutMode.name)
        addProperty("pickerSortBy", pickerSortBy.name)
        addProperty("pickerSortDescending", pickerSortDescending)
        val pickerArr = JsonArray()
        pickerVisibleFields.forEach { pickerArr.add(it.name) }
        add("pickerVisibleFields", pickerArr)
    }.toString()

    companion object {
        const val DEFAULT_AUTO_WIPE_THRESHOLD = 10
        val DEFAULT_VISIBLE_FIELDS: Set<VaultField> = setOf(
            VaultField.RESOLUTION,
            VaultField.SIZE
        )
        val DEFAULT_PICKER_VISIBLE_FIELDS: Set<VaultPickerField> = setOf(
            VaultPickerField.PATH,
            VaultPickerField.SIZE
        )

        fun fromJsonString(raw: String): VaultSettings {
            val o = JsonParser.parseString(raw).asJsonObject
            fun optInt(key: String, def: Int): Int =
                o.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: def
            fun optBool(key: String, def: Boolean): Boolean =
                o.get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: def
            fun optStr(key: String, def: String): String =
                o.get(key)?.takeIf { !it.isJsonNull }?.asString ?: def

            val fields = if (o.has("visibleFields") && o.get("visibleFields").isJsonArray) {
                val arr = o.getAsJsonArray("visibleFields")
                buildSet {
                    for (el in arr) {
                        if (!el.isJsonNull) {
                            runCatching { VaultField.valueOf(el.asString) }.getOrNull()?.let(::add)
                        }
                    }
                }
            } else {
                DEFAULT_VISIBLE_FIELDS
            }
            val pickerFields = if (o.has("pickerVisibleFields") && o.get("pickerVisibleFields").isJsonArray) {
                val arr = o.getAsJsonArray("pickerVisibleFields")
                buildSet {
                    for (el in arr) {
                        if (!el.isJsonNull) {
                            runCatching { VaultPickerField.valueOf(el.asString) }.getOrNull()?.let(::add)
                        }
                    }
                }
            } else {
                DEFAULT_PICKER_VISIBLE_FIELDS
            }
            return VaultSettings(
                autoWipeThreshold = optInt("autoWipeThreshold", DEFAULT_AUTO_WIPE_THRESHOLD),
                stealthMode = optBool("stealthMode", false),
                keepOpenOnPlayerBack = optBool("keepOpenOnPlayerBack", false),
                biometricsEnabled = optBool("biometricsEnabled", false),
                passwordManagerEnabled = optBool("passwordManagerEnabled", false),
                layoutMode = runCatching {
                    VaultLayoutMode.valueOf(optStr("layoutMode", VaultLayoutMode.GRID.name))
                }.getOrDefault(VaultLayoutMode.GRID),
                sortBy = runCatching {
                    VaultSortBy.valueOf(optStr("sortBy", VaultSortBy.DATE.name))
                }.getOrDefault(VaultSortBy.DATE),
                sortDescending = optBool("sortDescending", true),
                visibleFields = fields,
                pickerLayoutMode = runCatching {
                    VaultLayoutMode.valueOf(optStr("pickerLayoutMode", VaultLayoutMode.GRID.name))
                }.getOrDefault(VaultLayoutMode.GRID),
                pickerSortBy = runCatching {
                    VaultPickerSortBy.valueOf(optStr("pickerSortBy", VaultPickerSortBy.DATE.name))
                }.getOrDefault(VaultPickerSortBy.DATE),
                pickerSortDescending = optBool("pickerSortDescending", true),
                pickerVisibleFields = pickerFields
            )
        }
    }
}

/**
 * Unencrypted envelope header (`vault_envelope.json`) containing only salted password auth hashes,
 * wrapped DEKs, public ingest key, failure counter, and salted stealth-PIN HMAC verifier.
 */
data class VaultEnvelope(
    val version: Int = 1,
    val kdfIterations: Int = VaultCryptoPrimitives.PBKDF2_DEFAULT_ITERATIONS,
    val vaultSaltB64: String,
    val masterPasswordHashB64: String,
    val wrappedDekIvB64: String,
    val wrappedDekCtB64: String,
    val ingestPublicKeyB64: String,
    val ingestPrivateKeySaltB64: String,
    val ingestPrivateKeyIvB64: String,
    val ingestPrivateKeyCtB64: String,
    val biometricEnabled: Boolean = false,
    val biometricInvalidated: Boolean = false,
    val biometricWrappedDekIvB64: String? = null,
    val biometricWrappedDekCtB64: String? = null,
    val stealthEnabled: Boolean = false,
    val stealthPinSaltB64: String? = null,
    val stealthPinHmacB64: String? = null,
    val passwordManagerEnabled: Boolean = false,
    val decoySaltB64: String? = null,
    val decoyPasswordHashB64: String? = null,
    val decoyWrappedDekIvB64: String? = null,
    val decoyWrappedDekCtB64: String? = null,
    val failedAttempts: Int = 0,
    val lockoutUntilMs: Long = 0L,
    val autoWipeThreshold: Int = VaultSettings.DEFAULT_AUTO_WIPE_THRESHOLD
) {
    fun hasDecoy(): Boolean =
        !decoySaltB64.isNullOrBlank() &&
            !decoyPasswordHashB64.isNullOrBlank() &&
            !decoyWrappedDekIvB64.isNullOrBlank() &&
            !decoyWrappedDekCtB64.isNullOrBlank()

    fun hasStealthPin(): Boolean =
        !stealthPinSaltB64.isNullOrBlank() && !stealthPinHmacB64.isNullOrBlank()

    fun toJsonString(): String = JSONObject().apply {
        put("version", version)
        put("kdfIterations", kdfIterations)
        put("vaultSaltB64", vaultSaltB64)
        put("masterPasswordHashB64", masterPasswordHashB64)
        put("wrappedDekIvB64", wrappedDekIvB64)
        put("wrappedDekCtB64", wrappedDekCtB64)
        put("ingestPublicKeyB64", ingestPublicKeyB64)
        put("ingestPrivateKeySaltB64", ingestPrivateKeySaltB64)
        put("ingestPrivateKeyIvB64", ingestPrivateKeyIvB64)
        put("ingestPrivateKeyCtB64", ingestPrivateKeyCtB64)
        put("biometricEnabled", biometricEnabled)
        put("biometricInvalidated", biometricInvalidated)
        put("biometricWrappedDekIvB64", biometricWrappedDekIvB64 ?: JSONObject.NULL)
        put("biometricWrappedDekCtB64", biometricWrappedDekCtB64 ?: JSONObject.NULL)
        put("stealthEnabled", stealthEnabled)
        put("stealthPinSaltB64", stealthPinSaltB64 ?: JSONObject.NULL)
        put("stealthPinHmacB64", stealthPinHmacB64 ?: JSONObject.NULL)
        put("passwordManagerEnabled", passwordManagerEnabled)
        put("decoySaltB64", decoySaltB64 ?: JSONObject.NULL)
        put("decoyPasswordHashB64", decoyPasswordHashB64 ?: JSONObject.NULL)
        put("decoyWrappedDekIvB64", decoyWrappedDekIvB64 ?: JSONObject.NULL)
        put("decoyWrappedDekCtB64", decoyWrappedDekCtB64 ?: JSONObject.NULL)
        put("failedAttempts", failedAttempts)
        put("lockoutUntilMs", lockoutUntilMs)
        put("autoWipeThreshold", autoWipeThreshold)
    }.toString()

    companion object {
        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

        fun fromJsonString(raw: String): VaultEnvelope {
            val o = JSONObject(raw)
            return VaultEnvelope(
                version = o.optInt("version", 1),
                kdfIterations = o.optInt("kdfIterations", VaultCryptoPrimitives.PBKDF2_DEFAULT_ITERATIONS),
                vaultSaltB64 = o.getString("vaultSaltB64"),
                masterPasswordHashB64 = o.getString("masterPasswordHashB64"),
                wrappedDekIvB64 = o.getString("wrappedDekIvB64"),
                wrappedDekCtB64 = o.getString("wrappedDekCtB64"),
                ingestPublicKeyB64 = o.getString("ingestPublicKeyB64"),
                ingestPrivateKeySaltB64 = o.getString("ingestPrivateKeySaltB64"),
                ingestPrivateKeyIvB64 = o.getString("ingestPrivateKeyIvB64"),
                ingestPrivateKeyCtB64 = o.getString("ingestPrivateKeyCtB64"),
                biometricEnabled = o.optBoolean("biometricEnabled", false),
                biometricInvalidated = o.optBoolean("biometricInvalidated", false),
                biometricWrappedDekIvB64 = o.optStringOrNull("biometricWrappedDekIvB64"),
                biometricWrappedDekCtB64 = o.optStringOrNull("biometricWrappedDekCtB64"),
                stealthEnabled = o.optBoolean("stealthEnabled", false),
                stealthPinSaltB64 = o.optStringOrNull("stealthPinSaltB64"),
                stealthPinHmacB64 = o.optStringOrNull("stealthPinHmacB64"),
                passwordManagerEnabled = o.optBoolean("passwordManagerEnabled", false),
                decoySaltB64 = o.optStringOrNull("decoySaltB64"),
                decoyPasswordHashB64 = o.optStringOrNull("decoyPasswordHashB64"),
                decoyWrappedDekIvB64 = o.optStringOrNull("decoyWrappedDekIvB64"),
                decoyWrappedDekCtB64 = o.optStringOrNull("decoyWrappedDekCtB64"),
                failedAttempts = o.optInt("failedAttempts", 0),
                lockoutUntilMs = o.optLong("lockoutUntilMs", 0L),
                autoWipeThreshold = o.optInt("autoWipeThreshold", VaultSettings.DEFAULT_AUTO_WIPE_THRESHOLD)
            )
        }
    }
}
