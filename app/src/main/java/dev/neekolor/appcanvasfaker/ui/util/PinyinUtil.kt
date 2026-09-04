package dev.neekolor.appcanvasfaker.ui.util

import android.icu.text.Transliterator
import java.util.concurrent.ConcurrentHashMap

object PinyinUtil {

    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
    }

    // Transliterator 非线程安全且转写较重：结果按输入缓存，命中后零开销；
    // 未命中时在锁内串行转写（应用列表搜索的高频路径不再逐项重复计算）
    private val cache = ConcurrentHashMap<String, String>()

    fun toPinyin(input: String): String =
        cache.computeIfAbsent(input) { s ->
            synchronized(transliterator) { transliterator.transliterate(s).replace(" ", "") }
        }
}
