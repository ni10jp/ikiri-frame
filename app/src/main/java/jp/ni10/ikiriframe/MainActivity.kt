package jp.ni10.ikiriframe

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import jp.ni10.ikiriframe.ui.IkiriHome
import jp.ni10.ikiriframe.ui.IkiriTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) forwardLegacyShare(intent)
        setContent {
            IkiriTheme {
                IkiriHome(onExit = ::finish, onPhotoPicked = { uri ->
                    startActivity(EditorActivity.createIntent(this, uri))
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        forwardLegacyShare(intent)
    }

    private fun forwardLegacyShare(intent: Intent) {
        // Existing direct-share targets may still point at the old receiving Activity.
        if (intent.action == Intent.ACTION_SEND) {
            startActivity(Intent(intent).setClass(this, EditorActivity::class.java))
        }
    }
}
