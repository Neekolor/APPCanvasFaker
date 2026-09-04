package dev.neekolor.appcanvasfaker.scanner.fingerprint

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 采集编排器：统一调度 21 项指纹采集
 */
object FingerprintCollector {

    val items: List<FingerprintItem> = buildList {
        // A 组：像素读取出口
        add(FingerprintItem("A1", "getPixels", "A-像素读取", "整幅读入 IntArray 后 SHA-256"))
        add(FingerprintItem("A2", "getPixel(单点)", "A-像素读取", "固定坐标单点采样拼接哈希"))
        add(FingerprintItem("A3", "copyPixelsToBuffer", "A-像素读取", "像素灌入 ByteBuffer 后哈希"))
        add(FingerprintItem("A4", "compress(PNG)", "A-像素读取", "压缩成 PNG 字节流哈希"))
        add(FingerprintItem("A4b", "compress(JPEG)", "A-像素读取", "压缩成 JPEG 字节流哈希"))
        add(FingerprintItem("A5", "copy 克隆", "A-像素读取", "Bitmap.copy 副本哈希"))
        add(FingerprintItem("A5b", "createBitmap(源)", "A-像素读取", "从源派生副本哈希"))
        add(FingerprintItem("A6", "copyPixelsFromBuffer", "A-像素读取", "Buffer 灌入后再读回哈希"))
        add(FingerprintItem("A7", "compress 往返", "A-像素读取", "编码-解码往返一致性哈希"))
        // B 组：离屏渲染
        add(FingerprintItem("B1", "createBitmap+Canvas", "B-离屏渲染", "离屏渲染后读取哈希"))
        add(FingerprintItem("B1b", "离屏渲染+compress", "B-离屏渲染", "离屏渲染后编码哈希"))
        add(FingerprintItem("B2", "setBitmap+Canvas", "B-离屏渲染", "绑定后绘制读取哈希"))
        // C 组：视图抓取
        add(FingerprintItem("C1", "buildDrawingCache", "C-视图抓取", "View 绘制缓存哈希"))
        add(FingerprintItem("C2", "PixelCopy.request", "C-视图抓取", "系统拷贝帧缓冲哈希"))
        // D 组：硬件直读
        add(FingerprintItem("D1", "glReadPixels", "D-硬件直读", "GPU 帧缓冲直读哈希"))
        // E 组：非像素
        add(FingerprintItem("E1", "字体度量", "E-非像素", "measureText/breakText/FontMetrics"))
        add(FingerprintItem("E2", "Bitmap 元数据", "E-非像素", "宽高/Config/ColorSpace 元数据"))
        add(FingerprintItem("E3", "硬件加速状态", "E-非像素", "Canvas.isHardwareAccelerated"))
        add(FingerprintItem("E4", "BitmapRegionDecoder", "E-非像素", "分块解码区域像素哈希"))
        add(FingerprintItem("E5", "Parcel 序列化", "E-非像素", "像素数据经 Parcel 打包哈希"))
        // F 组：参考基线
        add(FingerprintItem("F1", "设备真实基线", "F-参考基线", "首次采集原始基线（getPixels）"))
    }

    /** 同步采集：所有不需要 Activity/View/GL 上下文的项 */
    suspend fun collectSync(context: Context): List<FingerprintResult> = withContext(Dispatchers.Default) {
        val standard = dev.neekolor.appcanvasfaker.scanner.core.StandardCanvas.createBitmap()
        val results = mutableListOf<FingerprintResult>()
        val map = items.associateBy { it.id }

        fun run(id: String, block: () -> String) {
            val item = map[id] ?: return
            val start = System.currentTimeMillis()
            val hash = runCatching { block() }.getOrElse { "异常: ${it.javaClass.simpleName}" }
            results += FingerprintResult(item, hash, System.currentTimeMillis() - start)
        }

        run("A1") { PixelReaders.getPixels(standard) }
        run("A2") { PixelReaders.getPixel(standard) }
        run("A3") { PixelReaders.copyPixelsToBuffer(standard) }
        run("A4") { PixelReaders.compressPng(standard) }
        run("A4b") { PixelReaders.compressJpeg(standard) }
        run("A5") { PixelReaders.copyClone(standard) }
        run("A5b") { PixelReaders.createBitmapFrom(standard) }
        run("A6") { PixelReaders.copyPixelsFromBuffer(standard) }
        run("A7") { PixelReaders.compressRoundTrip(standard) }
        run("B1") { OffscreenRenderers.createBitmapCanvas() }
        run("B1b") { OffscreenRenderers.createBitmapCanvasCompress() }
        run("B2") { OffscreenRenderers.setBitmapCanvas() }
        run("E1") { NonPixelSignals.fontMetrics() }
        run("E2") { NonPixelSignals.bitmapMetadata(standard) }
        run("E3") { NonPixelSignals.hardwareAcceleration() }
        run("E4") { NonPixelSignals.regionDecoder(standard) }
        run("E5") { NonPixelSignals.parcelSerialize(standard) }
        run("F1") {
            val baseline = dev.neekolor.appcanvasfaker.scanner.core.StandardCanvas.createBitmap()
            try {
                PixelReaders.getPixels(baseline)
            } finally {
                baseline.recycle()
            }
        }

        standard.recycle()
        results
    }
}