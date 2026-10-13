package app.liftos.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import app.liftos.data.ExportFiles

/** Clipboard, Share Sheet and "open link" helpers. Nothing here sends data anywhere by itself. */
object ShareHelper {

    /** Copies text to the clipboard. Returns true if copied. [sensitive] hides the preview on Android 13+. */
    fun copy(context: Context, label: String, text: String, sensitive: Boolean = false): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        cm.setPrimaryClip(clip)
        return true
    }

    fun shareText(context: Context, title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Writes [content] to a cache file and offers it through the Android Share Sheet (Drive, mail, ...). */
    fun shareFile(context: Context, fileName: String, mime: String, content: String, utf8Bom: Boolean = false) {
        val uri: Uri = ExportFiles.write(context, fileName, content, utf8Bom)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens a web link in the browser / YouTube app. Returns false if nothing can handle it. */
    fun openLink(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        Toast.makeText(context, "לא נמצאה אפליקציה לפתיחת הקישור", Toast.LENGTH_SHORT).show()
        false
    }
}
