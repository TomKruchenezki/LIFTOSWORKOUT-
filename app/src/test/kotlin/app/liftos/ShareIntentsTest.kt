package app.liftos

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.liftos.ui.util.ShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What the app hands to Android when the user copies or shares. The system chooser itself and the receiving apps
 * (Drive, mail, ChatGPT, Claude) are outside the app and NOT VERIFIED.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class ShareIntentsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun chooserTarget(chooser: Intent): Intent {
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        return chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    }

    @Test
    fun `copy puts the text on the clipboard`() {
        assertTrue(ShareHelper.copy(context, "דוח", "שלום 123"))
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("שלום 123", cm.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun `sharing text opens the Android share sheet with the text`() {
        ShareHelper.shareText(context, "סיכום", "תוכן הדוח")
        val send = chooserTarget(shadowOf(context).nextStartedActivity)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("תוכן הדוח", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `sharing a file goes through the app's own file provider with read permission`() {
        ShareHelper.shareFile(context, "liftos.csv", "text/csv", "a,b\n1,2\n", utf8Bom = true)
        val send = chooserTarget(shadowOf(context).nextStartedActivity)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/csv", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("app.liftos.fileprovider", uri.authority)
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertEquals("UTF-8 byte order mark so Excel reads Hebrew", listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        assertEquals("a,b\n1,2\n", String(bytes, 3, bytes.size - 3, Charsets.UTF_8))
    }

    @Test
    fun `a direct share to an app that is not installed reports failure instead of crashing`() {
        assertFalse(ShareHelper.shareTextTo(context, "com.example.not.installed", "דוח", "טקסט"))
        assertFalse(ShareHelper.openApp(context, "com.example.not.installed"))
    }

    @Test
    fun `a direct share to an installed app targets that package only`() {
        val pkg = "com.openai.chatgpt"
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Share" } }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(pkg), info)

        assertTrue(ShareHelper.shareTextTo(context, pkg, "דוח", "טקסט"))
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_SEND, started.action)
        assertEquals(pkg, started.`package`)
        assertEquals("טקסט", started.getStringExtra(Intent.EXTRA_TEXT))
    }
}
