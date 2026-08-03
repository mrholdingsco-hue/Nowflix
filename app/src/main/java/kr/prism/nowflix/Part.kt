package kr.prism.nowflix

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One row in `assets/parts.json`. The screen is rendered entirely from a list of
 * these, so adding a 7th/8th part is a data change only — no code edit.
 */
@Serializable
data class Part(
    val id: String,
    val title: String,
    val thumbnail: String,
    val playlistId: String,
    // Optional playlist blurb shown (2 lines, ellipsized) in the detail left panel.
    val description: String = "",
    // Remote thumbnail URL (from Supabase). Blank -> the app uses [thumbnail]'s drawable.
    val thumbnailUrl: String = "",
)

object PartsRepository {
    private val json = Json { ignoreUnknownKeys = true }

    /** Pure parse — no Android deps, so it is exercised by JVM unit tests. */
    fun parse(text: String): List<Part> = json.decodeFromString<List<Part>>(text)

    fun load(context: Context): List<Part> =
        context.assets.open("parts.json").bufferedReader().use { parse(it.readText()) }
}
