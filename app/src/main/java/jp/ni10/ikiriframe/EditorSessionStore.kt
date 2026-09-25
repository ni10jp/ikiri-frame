package jp.ni10.ikiriframe

import android.content.Context
import jp.ni10.ikiriframe.photo.FrameOptions
import jp.ni10.ikiriframe.photo.FrameText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LabelDraft(val values: List<String>, val tag: String, val reset: Boolean = false)

internal data class EditorSession(val id: String, val photoPath: String, val options: FrameOptions,
    val draft: LabelDraft?, val sourceUri: String?)

/** A small journal, independent of an Activity's saved-state snapshot and URI grant. */
internal class EditorSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("editor_session", Context.MODE_PRIVATE)

    fun read(expectedId: String? = null): EditorSession? = runCatching {
        val json = JSONObject(preferences.getString("current", null) ?: return null)
        val path = json.getString("photo")
        val id = json.getString("id")
        if (expectedId != null && expectedId != id) return null
        if (!File(path).isFile) return null
        fun JSONArray.values() = (0 until length()).map { getString(it) }
        val text = json.optJSONArray("text")?.values()?.takeIf { it.size == 6 }?.let(FrameText::from)
        val draft = json.optJSONObject("draft")?.let {
            val values = it.getJSONArray("values").values()
            if (values.size == 6) LabelDraft(values, it.getString("tag"), it.optBoolean("reset")) else null
        }
        EditorSession(id, path, FrameOptions(thickness = json.getDouble("thickness").toFloat(),
            extractTheme = json.getBoolean("extractTheme"), text = text, tag = json.getString("tag"),
            paletteId = json.optString("paletteId").takeIf { it.isNotEmpty() },
            darkTheme = json.optBoolean("darkTheme", false)), draft,
            json.optString("sourceUri").takeIf { it.isNotEmpty() })
    }.getOrNull()

    fun write(id: String, state: EditorState, sourceUri: String?) {
        val photo = state.photo ?: return
        val json = JSONObject().put("id", id).put("photo", photo.file.absolutePath)
            .put("thickness", state.options.thickness).put("extractTheme", state.options.extractTheme)
            .put("tag", state.options.tag).put("sourceUri", sourceUri)
            .put("paletteId", state.options.paletteId)
            .put("darkTheme", state.options.darkTheme)
        state.options.text?.let { json.put("text", JSONArray(it.values())) }
        state.labelDraft?.let { json.put("draft", JSONObject().put("values", JSONArray(it.values))
            .put("tag", it.tag).put("reset", it.reset)) }
        preferences.edit().putString("current", json.toString()).apply()
    }

    fun clear(photoPath: String?) {
        if (photoPath != null && read()?.photoPath == photoPath) preferences.edit().remove("current").apply()
    }
}
