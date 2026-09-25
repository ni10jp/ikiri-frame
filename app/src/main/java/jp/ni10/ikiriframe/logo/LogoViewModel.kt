package jp.ni10.ikiriframe.logo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LogoLibraryState(
    val logos: List<RegisteredLogo> = emptyList(),
    val selectedId: String? = null,
    val busy: Boolean = true,
    val initialLoading: Boolean = true,
)

class LogoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LogoRepository(application)
    private val mutableState = MutableStateFlow(LogoLibraryState(selectedId = repository.selectedId))
    val state = mutableState.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    init { viewModelScope.launch { reload() } }

    private suspend fun reload() {
        val logos = repository.list()
        val selected = repository.selectedId?.takeIf { id -> logos.any { it.id == id } }
        if (selected != repository.selectedId) repository.select(null)
        mutableState.value = LogoLibraryState(logos, selected, busy = false, initialLoading = false)
    }

    fun select(id: String?) {
        if (state.value.busy || (id != null && state.value.logos.none { it.id == id })) return
        repository.select(id)
        mutableState.update { it.copy(selectedId = id) }
    }

    fun register(uri: Uri) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repository.register(uri)
                reload()
                messages.send("ロゴを登録しました")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messages.send("登録できませんでした。SVG、または透過PNGを選んでください（8MB以下・PNGは400万画素以下）。")
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    // The file operation survives disposal of the row's composition.
    suspend fun delete(id: String): Boolean = viewModelScope.async {
        if (state.value.busy) return@async false
        try {
            repository.delete(id)
            mutableState.update { current ->
                current.copy(
                    logos = current.logos.filterNot { it.id == id },
                    selectedId = current.selectedId.takeUnless { it == id },
                )
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            messages.send("削除できませんでした")
            false
        }
    }.await()
}
