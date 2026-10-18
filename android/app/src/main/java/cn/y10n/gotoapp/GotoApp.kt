package cn.y10n.gotoapp

import android.app.Application
import cn.y10n.gotoapp.data.ConfigStore
import cn.y10n.gotoapp.server.LanServer

class GotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        ConfigStore.init(this)
        LanServer.start(this)
    }

    companion object {
        lateinit var instance: GotoApp
            private set
    }
}
