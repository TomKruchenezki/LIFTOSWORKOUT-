package app.liftos.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Writes export files to the app cache and exposes them to other apps via FileProvider. */
object ExportFiles {
    private const val DIR = "exports"

    fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** @param utf8Bom prepend a BOM so Excel opens Hebrew CSV files correctly. */
    fun write(context: Context, fileName: String, content: String, utf8Bom: Boolean = false): Uri {
        val bom = if (utf8Bom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) else ByteArray(0)
        return writeBytes(context, fileName, bom + content.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(context: Context, fileName: String, bytes: ByteArray): Uri {
        val (file, uri) = newFile(context, fileName)
        file.outputStream().use { it.write(bytes) }
        return uri
    }

    /** An empty cache file (and its FileProvider uri) for a caller that streams a large export into it. */
    fun newFile(context: Context, fileName: String): Pair<File, Uri> {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        // Keep the cache tidy: drop exports older than a day.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000L }?.forEach { it.delete() }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, safeName)
        return file to FileProvider.getUriForFile(context, authority(context), file)
    }
}
