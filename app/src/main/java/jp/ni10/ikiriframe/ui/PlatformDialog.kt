package jp.ni10.ikiriframe.ui

import android.view.ContextThemeWrapper
import android.view.ViewGroup.LayoutParams
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.ni10.ikiriframe.R

/** Compose content in a native dialog; Android owns back progress, cancellation and dismissal. */
@Composable
internal fun PlatformDialog(title: String, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val parentComposition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val dismiss by rememberUpdatedState(onDismissRequest)
    DisposableEffect(context, parentComposition, title) {
        val nativeDialog = ComponentDialog(ContextThemeWrapper(context, R.style.ThemeOverlay_IkiriFrame_Dialog))
        nativeDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        nativeDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        nativeDialog.setTitle(title)
        nativeDialog.setCanceledOnTouchOutside(true)
        val view = ComposeView(nativeDialog.context).apply {
            setParentCompositionContext(parentComposition)
            setContent {
                Box(Modifier.widthIn(min = 280.dp, max = 560.dp).semantics {
                    dialog()
                    paneTitle = title
                }, propagateMinConstraints = true) { currentContent() }
            }
        }
        nativeDialog.setContentView(view, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        // Observe a completed native cancellation; do not intercept the back gesture.
        nativeDialog.setOnCancelListener { dismiss() }
        nativeDialog.show()
        onDispose {
            nativeDialog.setOnCancelListener(null)
            nativeDialog.dismiss()
            view.disposeComposition()
        }
    }
}
