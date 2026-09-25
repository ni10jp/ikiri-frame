package jp.ni10.ikiriframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import jp.ni10.ikiriframe.ui.IkiriTheme
import jp.ni10.ikiriframe.ui.LogoLibraryScreen

/** Android owns the predictive back transition to the calling screen. */
class LogoSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { IkiriTheme { LogoLibraryScreen(onBack = ::finish) } }
    }
}
