package dev.neekolor.appcanvasfaker.core

import android.util.Log
import dev.neekolor.appcanvasfaker.acfApp
import java.lang.reflect.Proxy

/**
 * LSPosed 作用域门禁：读当前 scope + 申请加包。
 *
 * 全反射、零编译期耦合——HookedProcess / OnScopeEventListener 只存在于框架
 * 运行时，编译产物里没有；接口缺失（旧框架）或反射失败一律降级为未知，
 * 调用方此时必须隐藏提示、不误报。申请加包走框架侧用户确认窗，
 * 模块不能静默改自己的 scope（安全设计）。
 */
object ScopeGate {

    private const val TAG = "ScopeGate"
    private const val LISTENER_CLASS =
        "io.github.libxposed.service.XposedService\$OnScopeEventListener"

    /**
     * 当前作用域包名集合；null = 未知（服务未绑定 / 方法缺失 / 反射失败）。
     * binder 调用，禁止主线程。
     */
    fun scopedPackages(): Set<String>? {
        val service = acfApp.xposedService ?: return null
        return runCatching {
            val result = service.javaClass.getMethod("getScope").invoke(service) as? List<*>
                ?: return null
            result.mapNotNull { hp ->
                // 实测本机框架返回包名字符串；HookedProcess 形态走反射兜底
                (hp as? String)?.takeIf { it.isNotBlank() } ?: runCatching {
                    var c: Class<*>? = hp?.javaClass
                    var f: java.lang.reflect.Field? = null
                    while (c != null && c != Any::class.java && f == null) {
                        f = runCatching { c.getDeclaredField("packageName") }.getOrNull()
                        c = c.superclass
                    }
                    f?.apply { isAccessible = true }?.get(hp) as? String
                }.getOrNull()
            }.toSet()
        }.onFailure { Log.w(TAG, "getScope failed", it) }.getOrNull()
    }

    /**
     * 申请把包加入作用域：框架侧弹用户确认窗，通过/拒绝都经回调。
     * 回调在 binder 线程，调用方自行切线程。服务缺失/反射失败直接判失败。
     */
    fun requestScope(packageName: String, onResult: (approved: Boolean) -> Unit) {
        val service = acfApp.xposedService
        if (service == null) {
            onResult(false)
            return
        }
        runCatching {
            val loader = service.javaClass.classLoader
            val listenerIface = Class.forName(LISTENER_CLASS, false, loader)
            val proxy = Proxy.newProxyInstance(
                loader, arrayOf(listenerIface)
            ) { self, method, args ->
                // Object 方法必须自行兜底：框架侧把回调塞进 Map 会调 hashCode，
                // handler 回 null 会在拆箱 int 时 NPE（已在真机复现）。
                when (method.name) {
                    "hashCode" -> System.identityHashCode(self)
                    "equals" -> self === args?.getOrNull(0)
                    "toString" -> "ScopeGateCallback"
                    "onScopeRequestApproved" -> {
                        onResult(true)
                        null
                    }
                    "onScopeRequestFailed" -> {
                        onResult(false)
                        null
                    }
                    else -> null
                }
            }
            service.javaClass
                .getMethod("requestScope", List::class.java, listenerIface)
                .invoke(service, listOf(packageName), proxy)
        }.onFailure {
            Log.w(TAG, "requestScope failed", it)
            onResult(false)
        }
    }
}
