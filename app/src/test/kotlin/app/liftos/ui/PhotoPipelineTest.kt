package app.liftos.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import app.liftos.data.TestEnv
import app.liftos.data.repo.PhotoRepository
import app.liftos.domain.model.PhotoAngle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The real photo pipeline: decode, downsize, re-encode as JPEG into app-private storage. Needs the native graphics
 * mode so that bitmaps are real. The camera and gallery pickers themselves are system UI and are NOT VERIFIED here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoPipelineTest {
    private lateinit var env: TestEnv

    @Before
    fun setUp() {
        env = TestEnv()
    }

    @After
    fun tearDown() = env.close()

    private fun jpeg(width: Int, height: Int, name: String = "src.jpg"): File {
        val file = File(env.context.cacheDir, name)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return file
    }

    private fun size(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun `a big photo is stored downsized as a jpeg in private storage and the source is removed`() = runBlocking {
        val src = jpeg(4000, 3000)
        val id = env.photos.addFromFile(src, PhotoAngle.FRONT, env.now, 81.5, "  בוקר  ")
        assertNotNull(id)
        assertFalse("the temporary source is deleted", src.exists())

        val photo = env.photos.getAll().single()
        assertEquals(id, photo.id)
        assertEquals(PhotoAngle.FRONT, photo.angle)
        assertEquals(81.5, photo.weightKg!!, 0.0)
        assertEquals("notes are trimmed", "בוקר", photo.notes)
        assertEquals("Asia/Jerusalem", photo.zoneId)

        val stored = env.photos.fileOf(photo)
        assertTrue(stored.exists())
        assertTrue("it lives in the app's own files dir", stored.path.startsWith(env.context.filesDir.path))
        val (w, h) = size(stored)
        assertTrue("long edge capped at ${PhotoRepository.MAX_EDGE}: $w x $h", maxOf(w, h) <= PhotoRepository.MAX_EDGE)
        assertTrue("aspect ratio kept", Math.abs(w.toDouble() / h - 4.0 / 3.0) < 0.02)
    }

    @Test
    fun `a small photo keeps its size`() = runBlocking {
        val id = env.photos.addFromFile(jpeg(800, 600), PhotoAngle.SIDE, env.now, null, "")
        assertNotNull(id)
        val (w, h) = size(env.photos.fileOf(env.photos.getAll().single()))
        assertEquals(800 to 600, w to h)
    }

    @Test
    fun `a file that is not an image is refused and leaves nothing behind`() = runBlocking {
        val junk = File(env.context.cacheDir, "junk.jpg").apply { writeText("this is not a picture") }
        assertNull(env.photos.addFromFile(junk, PhotoAngle.BACK, env.now, null, ""))
        assertTrue(env.photos.getAll().isEmpty())
        assertTrue(env.photos.directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `the exif rotation of a phone photo is applied`() = runBlocking {
        val src = jpeg(1200, 800)
        ExifInterface(src.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        env.photos.addFromFile(src, PhotoAngle.FRONT, env.now, null, "")
        val (w, h) = size(env.photos.fileOf(env.photos.getAll().single()))
        assertEquals("landscape pixels with a 90 degree flag become portrait", 800 to 1200, w to h)
    }

    @Test
    fun `details can be edited and a photo can be deleted with its file`() = runBlocking {
        val id = env.photos.addFromFile(jpeg(600, 600), PhotoAngle.FRONT, env.now, 80.0, "a")!!
        env.photos.update(id, PhotoAngle.BACK, env.now - 86_400_000L, 79.0, " b ")
        val edited = env.photos.getAll().single()
        assertEquals(PhotoAngle.BACK, edited.angle)
        assertEquals(79.0, edited.weightKg!!, 0.0)
        assertEquals("b", edited.notes)

        val file = env.photos.fileOf(edited)
        assertTrue(file.exists())
        env.photos.delete(id)
        assertTrue(env.photos.getAll().isEmpty())
        assertFalse("the file is removed too", file.exists())
    }

    @Test
    fun `a stored photo loads as a small bitmap for lists and comparison`() = runBlocking {
        env.photos.addFromFile(jpeg(3000, 2000), PhotoAngle.FRONT, env.now, null, "")
        val bmp = env.photos.loadBitmap(env.photos.getAll().single(), 400)
        assertNotNull(bmp)
        assertTrue("loaded downsampled: ${bmp!!.width}", bmp.width < 2000)
    }

    @Test
    fun `delete all removes every photo and file`() = runBlocking {
        env.photos.addFromFile(jpeg(500, 500, "a.jpg"), PhotoAngle.FRONT, env.now, null, "")
        env.photos.addFromFile(jpeg(500, 500, "b.jpg"), PhotoAngle.SIDE, env.now, null, "")
        assertEquals(2, env.photos.getAll().size)
        env.photos.deleteAll()
        assertTrue(env.photos.getAll().isEmpty())
        assertTrue(env.photos.directory.listFiles().orEmpty().isEmpty())
    }
}
