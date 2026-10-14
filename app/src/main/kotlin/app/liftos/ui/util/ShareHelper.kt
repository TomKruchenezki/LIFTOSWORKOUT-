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

    /**
     * Hands [text] straight to one installed app (for example ChatGPT or Claude) through a package-targeted share.
     * Returns false when that app is not installed or does not accept shared text. Nothing leaves the phone from here:
     * the other app receives the text and the user decides whether to send it.
     */
    fun shareTextTo(context: Context, packageName: String, title: String, text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(packageName)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivity(send, 0) == null) return false
        return try {
            context.startActivity(send)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Writes [content] to a cache file and offers it through the Android Share Sheet (Drive, mail, ...). */
    fun shareFile(context: Context, fileName: String, mime: String, content: String, utf8Bom: Boolean = false) {
        shareUri(context, fileName, mime, ExportFiles.write(context, fileName, content, utf8Bom))
    }

    fun shareBytes(context: Context, fileName: String, mime: String, bytes: ByteArray) {
        shareUri(context, fileName, mime, ExportFiles.writeBytes(context, fileName, bytes))
    }

    fun shareUri(context: Context, fileName: String, mime: String, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Opens another installed app (for example the ChatGPT or Claude app) so the user can paste a report that was
     * already copied. Returns false when the app is not installed; nothing is sent by this call.
     */
    fun openApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
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
