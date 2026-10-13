package app.liftos

import android.app.Application
import app.liftos.di.AppContainer

class LiftOsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
