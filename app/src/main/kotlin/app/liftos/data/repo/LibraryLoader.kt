package app.liftos.data.repo

import android.content.Context
import app.liftos.domain.library.ExerciseLibrary
import app.liftos.domain.library.Library

/** Reads the built-in exercise library (assets/library/*.json). */
class LibraryLoader(private val context: Context) {
    fun load(): Library {
        val assets = context.assets
        val files = (assets.list(DIR) ?: emptyArray()).filter { it.endsWith(".json") }.sorted()
        val texts = files.map { name ->
            assets.open("$DIR/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        return ExerciseLibrary.parse(texts)
    }

    companion object {
        const val DIR = "library"
    }
}
