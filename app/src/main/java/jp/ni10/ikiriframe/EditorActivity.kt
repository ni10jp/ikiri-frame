package jp.ni10.ikiriframe

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import jp.ni10.ikiriframe.photo.SharedImage
import jp.ni10.ikiriframe.ui.IkiriEditor
import jp.ni10.ikiriframe.ui.IkiriTheme

/** Editing uses a separate window; its Compose screen confirms before finishing. */
class EditorActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[EditorViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receiveSharedImage(intent)
        setContent {
            IkiriTheme { IkiriEditor(onExit = ::finish, model = model, onSelectLogo = {
                startActivity(Intent(this, LogoSelectionActivity::class.java))
            }) }
        }
    }

    override fun onResume() {
        super.onResume()
        model.resumeEditing()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedImage(intent)
    }

    private fun receiveSharedImage(intent: Intent) {
        val uri = SharedImage.fromIntent(intent)
        if (uri != null) model.importSharedPhoto(uri) else model.rejectSharedPhoto()
    }

    companion object {
        fun createIntent(context: Context, uri: Uri) = Intent(context, EditorActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Photo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
