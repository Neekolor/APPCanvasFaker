package dev.neekolor.appcanvasfaker.scanner.fingerprint

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import dev.neekolor.appcanvasfaker.util.HashUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * D 组：硬件层直读（D1）
 *
 * 使用离屏 EGL 上下文（PbufferSurface）在任意线程执行真实 GL 渲染与
 * glReadPixels，无需 GLSurfaceView（避免独立 Surface 层遮挡 Compose UI）。
 */
object HardwareReaders {

    /** D1: 创建离屏 EGL 上下文 → 绘制标准内容 → glReadPixels 读帧缓冲后哈希 */
    fun glReadPixels(width: Int = 128, height: Int = 128): String {
        return try {
            val (display, config) = createEglDisplay() ?: return "EGL初始化失败"
            val context = createEglContext(display, config) ?: run {
                EGL14.eglTerminate(display)
                return "EGL上下文创建失败"
            }
            val surface = createPbufferSurface(display, config, width, height) ?: run {
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                return "PbufferSurface创建失败"
            }
            try {
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    return "eglMakeCurrent失败"
                }
                renderAndRead(width, height)
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                EGL14.eglReleaseThread()
            }
        } catch (e: Throwable) {
            "异常: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun createEglDisplay(): Pair<EGLDisplay, EGLConfig>? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            EGL14.eglTerminate(display)
            return null
        }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            EGL14.eglTerminate(display)
            return null
        }
        return display to configs[0]!!
    }

    private fun createEglContext(display: EGLDisplay, config: EGLConfig): EGLContext? {
        val attribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        return EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attribs, 0)
    }

    private fun createPbufferSurface(display: EGLDisplay, config: EGLConfig, w: Int, h: Int): EGLSurface? {
        val attribs = intArrayOf(
            EGL14.EGL_WIDTH, w,
            EGL14.EGL_HEIGHT, h,
            EGL14.EGL_NONE
        )
        return EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
    }

    /** 在已绑定的 EGL 上下文中：绘制标准内容并 glReadPixels */
    private fun renderAndRead(width: Int, height: Int): String {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(18f / 255f, 20f / 255f, 32f / 255f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 与 StandardCanvas 近似的图形：蓝色圆角矩形 + 珊瑚色圆形（D1 为独立指纹，不与其他项比对）
        drawRect(
            left = width * 0.06f, top = height * 0.11f,
            right = width * 0.48f, bottom = height * 0.53f,
            color = floatArrayOf(130f / 255f, 177f / 255f, 255f / 255f, 1f)
        )
        drawCircle(
            cx = width * 0.74f, cy = height * 0.34f, radius = height * 0.21f,
            color = floatArrayOf(255f / 255f, 171f / 255f, 145f / 255f, 1f)
        )

        GLES20.glFinish()

        val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) return "GL错误($error)"

        // 不依赖 direct buffer 的 array() 访问能力（规范上不保证可用），显式拷出字节
        buf.rewind()
        val pixels = ByteArray(buf.remaining())
        buf.get(pixels)

        // GL 是 bottom-up，翻转行序使与画布坐标系一致
        val row = ByteArray(width * 4)
        for (y in 0 until height / 2) {
            val top = y * width * 4
            val bottom = (height - 1 - y) * width * 4
            System.arraycopy(pixels, top, row, 0, row.size)
            System.arraycopy(pixels, bottom, pixels, top, row.size)
            System.arraycopy(row, 0, pixels, bottom, row.size)
        }
        return HashUtils.ofBytes(pixels)
    }

    private fun drawRect(left: Float, top: Float, right: Float, bottom: Float, color: FloatArray) {
        val vs = "attribute vec4 aPos; void main(){ gl_Position = aPos; }"
        val fs = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor = uColor; }"
        val program = createProgram(vs, fs)
        if (program == 0) return
        val posHandle = GLES20.glGetAttribLocation(program, "aPos")
        val colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        val verts = floatArrayOf(
            left, bottom, 0f,
            right, bottom, 0f,
            left, top, 0f,
            right, top, 0f
        )
        val vbuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
        vbuf.asFloatBuffer().put(verts)
        vbuf.position(0)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, vbuf)
        GLES20.glUniform4f(colorHandle, color[0], color[1], color[2], color[3])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDeleteProgram(program)
    }

    private fun drawCircle(cx: Float, cy: Float, radius: Float, color: FloatArray) {
        val segments = 48
        val verts = FloatArray((segments + 2) * 3)
        verts[0] = cx; verts[1] = cy; verts[2] = 0f
        for (i in 0..segments) {
            val angle = Math.PI * 2 * i / segments
            verts[(i + 1) * 3] = cx + (Math.cos(angle) * radius).toFloat()
            verts[(i + 1) * 3 + 1] = cy + (Math.sin(angle) * radius).toFloat()
            verts[(i + 1) * 3 + 2] = 0f
        }
        val vs = "attribute vec4 aPos; void main(){ gl_Position = aPos; }"
        val fs = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor = uColor; }"
        val program = createProgram(vs, fs)
        if (program == 0) return
        val posHandle = GLES20.glGetAttribLocation(program, "aPos")
        val colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        val vbuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
        vbuf.asFloatBuffer().put(verts)
        vbuf.position(0)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, vbuf)
        GLES20.glUniform4f(colorHandle, color[0], color[1], color[2], color[3])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, segments + 2)
        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDeleteProgram(program)
    }

    private fun createProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        // 校验链接状态：失败时返回 0，由调用方跳过绘制并依赖最终 glGetError 上报
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return if (linkStatus[0] == GLES20.GL_TRUE) program else 0.also {
            GLES20.glDeleteProgram(program)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.w(TAG, "shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private const val TAG = "HardwareReaders"
}