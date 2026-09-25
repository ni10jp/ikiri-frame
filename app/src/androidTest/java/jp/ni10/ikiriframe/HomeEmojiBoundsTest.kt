package jp.ni10.ikiriframe

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeEmojiBoundsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun portraitStageIncludesStatusAndNavigationBarAreas() =
        checkBounds(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)

    @Test fun landscapeStageIncludesSystemBarsAndCutoutArea() =
        checkBounds(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)

    private fun checkBounds(requested: Int, expected: Int) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = requested }
            compose.waitUntil(15_000) {
                var ready = false
                scenario.onActivity { ready = it.resources.configuration.orientation == expected }
                ready
            }
            compose.waitForIdle()
            val stage = compose.onNodeWithTag("home-emoji-stage").fetchSemanticsNode().boundsInRoot
            scenario.onActivity {
                val window = it.windowManager.currentWindowMetrics.bounds
                val bars = ViewCompat.getRootWindowInsets(it.window.decorView)!!
                    .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                assertTrue("Exercise a window with real system insets", bars.top + bars.bottom + bars.left + bars.right > 0)
                assertEquals("No inset on the left wall", 0f, stage.left, 1f)
                assertEquals("No inset on the top wall", 0f, stage.top, 1f)
                assertEquals("Model and shadow cover the whole display width", window.width().toFloat(), stage.right, 1f)
                assertEquals("Model and shadow extend behind the navigation bar", window.height().toFloat(), stage.bottom, 1f)
            }
        }
    }
}
