package app.liftos

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The privacy promises as tests: the merged manifest asks only for what the workout timer needs, nothing can open the
 * app's components from outside, and no network code exists. Offline-only is therefore enforced by the OS: with no
 * INTERNET permission any connection attempt fails.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestPolicyTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun packageInfo(flags: Int) = context.packageManager.getPackageInfo(context.packageName, flags)

    @Test
    fun `the app asks only for the permissions the timer needs and never for internet or storage`() {
        val all = packageInfo(PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        // AndroidX adds one private signature permission named after the package (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION):
        // it only protects the app's own broadcast receivers from other apps and is not shown to the user.
        val own = all.filter { it.startsWith("app.liftos.") }
        assertEquals(listOf("app.liftos.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"), own)
        val requested = all - own.toSet()
        assertEquals(
            setOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
                Manifest.permission.VIBRATE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
            ),
            requested,
        )
        assertFalse(Manifest.permission.INTERNET in requested)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in requested)
        assertFalse(Manifest.permission.CAMERA in requested)
    }

    @Test
    fun `package id and version are set`() {
        val info = packageInfo(0)
        assertEquals("app.liftos", info.packageName)
        assertTrue(info.versionName.orEmpty().isNotBlank())
        assertTrue(info.longVersionCode > 0)
    }

    @Test
    fun `android auto backup is off and only the launcher activity of the app is exported`() {
        assertEquals("the app has its own backup", 0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val info = packageInfo(
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS,
        )
        // Libraries add their own components (for example the Compose test activity in debug builds): only ours count.
        val exported = buildList {
            info.activities.orEmpty().filter { it.exported }.forEach { add(it.name) }
            info.services.orEmpty().filter { it.exported }.forEach { add(it.name) }
            info.receivers.orEmpty().filter { it.exported }.forEach { add(it.name) }
            info.providers.orEmpty().filter { it.exported }.forEach { add(it.name) }
        }
        assertEquals(listOf("app.liftos.MainActivity"), exported.filter { it.startsWith("app.liftos.") })
    }

    @Test
    fun `the source has no network code`() {
        val root = File("src/main/kotlin")
        assumeTrue("run from the module directory", root.isDirectory)
        val forbidden = listOf("HttpURLConnection", "java.net.URL(", "okhttp", "retrofit", "ktor", "WebView", "Socket(")
        val hits = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            forbidden.filter { it in text }.map { "${f.name}: $it" }
        }.toList()
        assertTrue("network code found: $hits", hits.isEmpty())
    }

    @Test
    fun `there is no nutrition module`() {
        val roots = listOf(File("src/main/kotlin"), File("../domain/src/main/kotlin"))
        assumeTrue("run from the module directory", roots.all { it.isDirectory })
        val forbidden = listOf("nutrition", "calorie", "calories", "קלוריות", "תזונה", "macro", "recipe")
        val hits = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
                val text = f.readText().lowercase()
                forbidden.filter { it in text || it in f.path.lowercase() }.map { "${f.name}: $it" }
            }.toList()
        }
        assertTrue("nutrition code found: $hits", hits.isEmpty())
    }
}
