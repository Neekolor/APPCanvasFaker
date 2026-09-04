package dev.neekolor.appcanvasfaker

import android.app.Application
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.RemoteBridge
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

lateinit var acfApp: AppCanvasFakerApplication

class AppCanvasFakerApplication : Application(), XposedServiceHelper.OnServiceListener {

    @Volatile
    var xposedService: XposedService? = null
        private set

    // onServiceBind/onServiceDied 来自框架回调线程，与主线程的 add/remove 并发，需线程安全集合
    private val serviceListeners = CopyOnWriteArrayList<() -> Unit>()

    override fun onCreate() {
        super.onCreate()
        acfApp = this
        XposedServiceHelper.registerListener(this)
    }

    fun addServiceListener(listener: () -> Unit) {
        serviceListeners.add(listener)
        xposedService?.let { listener.invoke() }
    }

    fun removeServiceListener(listener: () -> Unit) {
        serviceListeners.remove(listener)
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        RemoteBridge.service = service
        // 绑定瞬间把本地配置推远端（本地胜出），hook 侧即时可见
        runCatching { ConfigRepository(this).pushLocalConfigToRemote() }
        serviceListeners.forEach { it.invoke() }
    }

    override fun onServiceDied(service: XposedService) {
        if (xposedService == service) {
            xposedService = null
            RemoteBridge.service = null
            serviceListeners.forEach { it.invoke() }
        }
    }
}