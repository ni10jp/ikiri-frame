package jp.ni10.ikiriframe.photo

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Accept one image from a granted content URI, as used by Android's Sharesheet. */
object SharedImage {
    fun fromIntent(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return runCatching {
            val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            val clip = intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
            (stream ?: clip)?.takeIf { it.scheme == "content" }
        }.getOrNull()
    }
}
