package jp.ni10.ikiriframe

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import jp.ni10.ikiriframe.logo.LogoRepository
import jp.ni10.ikiriframe.logo.RegisteredLogo
import jp.ni10.ikiriframe.photo.FrameText
import jp.ni10.ikiriframe.photo.BuiltInPhotoPalettes
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import jp.ni10.ikiriframe.photo.FrameGeometry
import jp.ni10.ikiriframe.photo.FrameOptions
import jp.ni10.ikiriframe.photo.FramePalette
import jp.ni10.ikiriframe.photo.Photo
import jp.ni10.ikiriframe.photo.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorState(
    val photo: Photo? = null,
    val options: FrameOptions = FrameOptions(),
    val logo: RegisteredLogo? = null,
    val logoLoading: Boolean = false,
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val labelDraft: LabelDraft? = null,
)

class EditorViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)
    private val sessions = EditorSessionStore(application)
    private val logos = LogoRepository(application)
    private var logoJob: Job? = null
    private var finished = false
    private val preferences = application.getSharedPreferences("frame_options", Context.MODE_PRIVATE)
    private val sessionId = savedState.get<String>("edit_session_id")
        ?: UUID.randomUUID().toString().also { savedState["edit_session_id"] = it }
    private val journal = sessions.read(sessionId)
    private var sourceUri = journal?.sourceUri ?: savedState.get<String>("imported_uri")
    private val mutableState = MutableStateFlow(EditorState(options = FrameOptions(
        text = savedState.get<ArrayList<String>>("frame_text")?.takeIf { it.size == 6 }?.let(FrameText::from),
        tag = savedState["tag"] ?: "",
        thickness = savedState["thickness"] ?: preferences.getFloat("thickness", 1f),
        extractTheme = savedState["extract_theme"] ?: preferences.getBoolean("extract_theme", false),
        paletteId = savedState["palette_id"] ?: preferences.getString("custom_palette_id", null),
        darkTheme = savedState["dark_theme"] ?: preferences.getBoolean("dark_theme", false),
    )).let { initial -> journal?.let { initial.copy(options = it.options, labelDraft = it.draft) } ?: initial.copy(
        labelDraft = savedState.get<ArrayList<String>>("label_draft_values")?.takeIf { it.size == 6 }?.let {
            LabelDraft(it, savedState["label_draft_tag"] ?: "", savedState["label_draft_reset"] ?: false)
        }) })
    val state = mutableState.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    init {
        refreshLogo()
        if (journal != null && sourceUri == savedState.get<String>("pending_shared_uri")) {
            savedState.remove<String>("pending_shared_uri")
        }
        val path = journal?.photoPath ?: savedState.get<String>("photo_path")
        if (path == null) {
            importPendingShareIfIdle()
        } else {
            restorePhoto(path)
        }
    }

    private fun restorePhoto(path: String) {
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val photo = repository.restorePhoto(path)
                savedState["photo_path"] = photo.file.absolutePath
                mutableState.update { it.copy(photo = photo) }
                persistSession()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                savedState.remove<String>("photo_path")
                messages.send(R.string.load_error)
            } finally {
                mutableState.update { it.copy(loading = false) }
                importPendingShareIfIdle()
            }
        }
    }

    fun importSharedPhoto(uri: Uri) {
        // Preserve the most recent incoming image while a load or export owns the current file.
        // The receiving task's URI grant stays valid while we copy it into private app storage.
        savedState["pending_shared_uri"] = uri.toString()
        importPendingShareIfIdle()
    }

    fun rejectSharedPhoto() { messages.trySend(R.string.share_error) }

    private fun importPendingShareIfIdle() {
        if (finished || state.value.loading || state.value.exporting) return
        savedState.get<String>("pending_shared_uri")?.let { loadPhoto(Uri.parse(it), shared = true) }
    }

    private fun loadPhoto(uri: Uri, shared: Boolean) {
        if (state.value.loading || state.value.exporting) return
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val photo = repository.importPhoto(uri)
                sourceUri = uri.toString()
                savedState["photo_path"] = photo.file.absolutePath
                savedState.remove<ArrayList<String>>("frame_text")
                mutableState.update { it.copy(photo = photo, options = it.options.copy(text = null, tag = "",
                    paletteId = it.options.paletteId.takeIf(BuiltInPhotoPalettes::contains)), labelDraft = null) }
                persistSession()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messages.send(R.string.load_error)
            } finally {
                if (shared && savedState.get<String>("pending_shared_uri") == uri.toString()) {
                    savedState.remove<String>("pending_shared_uri")
                }
                mutableState.update { it.copy(loading = false) }
                importPendingShareIfIdle()
            }
        }
    }

    fun refreshLogo() {
        logoJob?.cancel()
        mutableState.update { it.copy(logoLoading = true) }
        logoJob = viewModelScope.launch {
            try {
                val logo = logos.selected()
                mutableState.update { it.copy(logo = logo) }
            } finally {
                if (currentCoroutineContext().isActive) mutableState.update { it.copy(logoLoading = false) }
            }
        }
    }

    fun resumeEditing() {
        if (finished) return
        persistSession()
        refreshLogo()
    }

    fun setText(text: FrameText) {
        val normalized = FrameText.from(text.values().map { it.replace("\n", " ").replace("\r", " ").take(120) })
        savedState["frame_text"] = ArrayList(normalized.values())
        mutableState.update { it.copy(options = it.options.copy(text = normalized)) }
        persistSession()
    }

    fun resetText() {
        savedState.remove<ArrayList<String>>("frame_text")
        mutableState.update { it.copy(options = it.options.copy(text = null)) }
        persistSession()
    }

    fun setTag(value: String) {
        val tag = value.replace("\n", " ").replace("\r", " ").take(120)
        savedState["tag"] = tag
        mutableState.update { it.copy(options = it.options.copy(tag = tag)) }
        persistSession()
    }

    fun setThickness(value: Float) {
        val thickness = value.coerceIn(FrameGeometry.MinimumThickness, FrameGeometry.MaximumThickness)
        savedState["thickness"] = thickness
        preferences.edit().putFloat("thickness", thickness).apply()
        mutableState.update { it.copy(options = it.options.copy(thickness = thickness)) }
        persistSession()
    }

    fun setLabelDraft(draft: LabelDraft?) {
        mutableState.update { it.copy(labelDraft = draft) }
        persistSession()
    }

    fun setPalette(id: String?) {
        val snapshot = state.value
        if (snapshot.loading || snapshot.exporting || snapshot.logoLoading || snapshot.photo == null) return
        if (id != null && snapshot.photo.theme?.palettes?.none { it.id == id } != false) return
        val extractTheme = id != null
        preferences.edit().putBoolean("extract_theme", extractTheme)
            .putString("custom_palette_id", id.takeIf(BuiltInPhotoPalettes::contains)).apply()
        mutableState.update { it.copy(options = it.options.copy(extractTheme = extractTheme, paletteId = id)) }
        persistSession()
    }

    fun setDarkTheme(darkTheme: Boolean) {
        val snapshot = state.value
        if (snapshot.loading || snapshot.exporting || snapshot.logoLoading || snapshot.photo == null) return
        preferences.edit().putBoolean("dark_theme", darkTheme).apply()
        mutableState.update { it.copy(options = it.options.copy(darkTheme = darkTheme)) }
        persistSession()
    }

    fun saveLabel(draft: LabelDraft) {
        val text = if (draft.reset) null else FrameText.from(draft.values.map(::singleLine))
        val tag = singleLine(draft.tag)
        mutableState.update { it.copy(options = it.options.copy(text = text, tag = tag), labelDraft = null) }
        persistSession()
    }

    private fun singleLine(value: String) = value.replace("\n", " ").replace("\r", " ").take(120)

    private fun persistSession() {
        if (finished) return
        val snapshot = state.value
        savedState["frame_text"] = snapshot.options.text?.let { ArrayList(it.values()) }
        savedState["tag"] = snapshot.options.tag
        savedState["thickness"] = snapshot.options.thickness
        savedState["extract_theme"] = snapshot.options.extractTheme
        savedState["palette_id"] = snapshot.options.paletteId
        savedState["dark_theme"] = snapshot.options.darkTheme
        savedState["label_draft_values"] = snapshot.labelDraft?.let { ArrayList(it.values) }
        savedState["label_draft_tag"] = snapshot.labelDraft?.tag
        savedState["label_draft_reset"] = snapshot.labelDraft?.reset
        savedState["imported_uri"] = sourceUri
        sessions.write(sessionId, snapshot, sourceUri)
    }

    fun finishEditing() {
        finished = true
        viewModelScope.coroutineContext.cancelChildren()
        sessions.clear(state.value.photo?.file?.absolutePath ?: savedState.get<String>("photo_path"))
    }

    fun export(palette: FramePalette, destination: Uri? = null) {
        val snapshot = state.value
        val photo = snapshot.photo ?: return
        if (snapshot.exporting || snapshot.loading || snapshot.logoLoading) return
        mutableState.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                repository.export(photo, snapshot.options, palette, snapshot.logo?.artwork, destination)
                messages.send(R.string.saved)
            } catch (error: CancellationException) {
                throw error
            } catch (_: OutOfMemoryError) {
                messages.send(R.string.export_error)
            } catch (_: Exception) {
                messages.send(R.string.export_error)
            } finally {
                mutableState.update { it.copy(exporting = false) }
                importPendingShareIfIdle()
            }
        }
    }
}
