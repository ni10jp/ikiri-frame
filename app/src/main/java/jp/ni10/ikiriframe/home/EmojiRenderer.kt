package jp.ni10.ikiriframe.home

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

internal data class BallPose(val x: Float, val y: Float, val radius: Float, val rotation: FloatArray,
    val impactNormalX: Float = 0f, val impactNormalY: Float = 0f, val compression: Float = 0f)

/** Small GLES renderer for the bundled colored mesh. EGL stays entirely on its own thread. */
internal class EmojiRenderer(context: Context) : TextureView.SurfaceTextureListener {
    private val assets = context.applicationContext.assets
    private val thread = HandlerThread("HomeEmojiGL").apply { start() }
    private val handler = Handler(thread.looper)
    private val queued = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var pose = BallPose(0f, 0f, 44f, floatArrayOf(0f, 0f, 0f, 1f))
    private var display = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private var texture: SurfaceTexture? = null
    private var width = 1
    private var height = 1
    private var meshProgram = 0
    private var shadowProgram = 0
    private var meshBuffer = 0
    private var vertices = 0
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)); position(0)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        handler.post {
            if (closed) return@post
            try {
                initialize(surface, width, height)
                draw()
            } catch (error: RuntimeException) {
                Log.e("HomeEmoji", "Unable to initialize emoji renderer", error)
                release()
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        handler.post { this.width = width; this.height = height; draw() }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        handler.post {
            if (texture === surface) release()
            surface.release()
        }
        return false
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    fun render(next: BallPose) {
        if (closed) return
        pose = next
        if (queued.compareAndSet(false, true)) handler.post {
            queued.set(false)
            if (!closed) draw()
        }
    }

    fun close() {
        closed = true
        handler.post { release(); thread.quitSafely() }
    }

    private fun initialize(texture: SurfaceTexture, width: Int, height: Int) {
        release()
        this.texture = texture; this.width = width; this.height = height
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(display, null, 0, null, 0))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0)
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        surface = EGL14.eglCreateWindowSurface(display, configs[0], texture, intArrayOf(EGL14.EGL_NONE), 0)
        check(surface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, surface, surface, eglContext))
        meshProgram = program(MeshVertex, MeshFragment)
        shadowProgram = program(ShadowVertex, ShadowFragment)
        val bytes = assets.open("models/star-struck.mesh").use { it.readBytes() }
        val data = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).apply { put(bytes); flip() }
        check(data.int == 0x314d4b49)
        vertices = data.int
        check(vertices > 0 && data.remaining() == vertices * 9 * 4)
        val buffer = IntArray(1); glGenBuffers(1, buffer, 0); meshBuffer = buffer[0]
        glBindBuffer(GL_ARRAY_BUFFER, meshBuffer)
        glBufferData(GL_ARRAY_BUFFER, data.remaining(), data, GL_STATIC_DRAW)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    private fun draw() {
        if (surface == EGL14.EGL_NO_SURFACE || width <= 0 || height <= 0) return
        val frame = pose
        // Keep the compressed face against its contact plane rather than opening a gap.
        val centerX = frame.x + frame.impactNormalX * frame.radius * frame.compression
        val centerY = frame.y + frame.impactNormalY * frame.radius * frame.compression
        glViewport(0, 0, width, height)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND); glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glUseProgram(shadowProgram)
        glUniform2f(glGetUniformLocation(shadowProgram, "viewport"), width.toFloat(), height.toFloat())
        glUniform2f(glGetUniformLocation(shadowProgram, "center"), centerX + frame.radius * .1f, centerY + frame.radius * .17f)
        glUniform1f(glGetUniformLocation(shadowProgram, "radius"), frame.radius * 1.28f)
        glUniform3f(glGetUniformLocation(shadowProgram, "deformation"), frame.impactNormalX, frame.impactNormalY, frame.compression)
        val corner = glGetAttribLocation(shadowProgram, "corner")
        glEnableVertexAttribArray(corner)
        glVertexAttribPointer(corner, 2, GL_FLOAT, false, 0, quad)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(corner)
        glDisable(GL_BLEND); glEnable(GL_DEPTH_TEST)
        glUseProgram(meshProgram)
        glUniform2f(glGetUniformLocation(meshProgram, "viewport"), width.toFloat(), height.toFloat())
        glUniform2f(glGetUniformLocation(meshProgram, "center"), centerX, centerY)
        glUniform1f(glGetUniformLocation(meshProgram, "radius"), frame.radius)
        glUniform4fv(glGetUniformLocation(meshProgram, "rotation"), 1, frame.rotation, 0)
        glUniform3f(glGetUniformLocation(meshProgram, "deformation"), frame.impactNormalX, frame.impactNormalY, frame.compression)
        glBindBuffer(GL_ARRAY_BUFFER, meshBuffer)
        val attributes = listOf("position", "normal", "color").map { glGetAttribLocation(meshProgram, it) }
        attributes.forEachIndexed { index, location ->
            glEnableVertexAttribArray(location)
            glVertexAttribPointer(location, 3, GL_FLOAT, false, 36, index * 12)
        }
        glDrawArrays(GL_TRIANGLES, 0, vertices)
        attributes.forEach { glDisableVertexAttribArray(it) }
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        if (!EGL14.eglSwapBuffers(display, surface)) release()
    }

    private fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
        }
        surface = EGL14.EGL_NO_SURFACE; eglContext = EGL14.EGL_NO_CONTEXT; display = EGL14.EGL_NO_DISPLAY
        texture = null
    }

    private fun program(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int {
            val id = glCreateShader(type); glShaderSource(id, source); glCompileShader(id)
            val success = IntArray(1); glGetShaderiv(id, GL_COMPILE_STATUS, success, 0)
            check(success[0] != 0) { glGetShaderInfoLog(id) }
            return id
        }
        val vs = shader(GL_VERTEX_SHADER, vertex); val fs = shader(GL_FRAGMENT_SHADER, fragment)
        val program = glCreateProgram(); glAttachShader(program, vs); glAttachShader(program, fs); glLinkProgram(program)
        glDeleteShader(vs); glDeleteShader(fs)
        val success = IntArray(1); glGetProgramiv(program, GL_LINK_STATUS, success, 0)
        check(success[0] != 0) { glGetProgramInfoLog(program) }
        return program
    }

    companion object {
        private const val MeshVertex = """
            attribute vec3 position; attribute vec3 normal; attribute vec3 color;
            uniform vec2 viewport; uniform vec2 center; uniform float radius; uniform vec4 rotation;
            uniform vec3 deformation;
            varying vec3 n; varying vec3 c;
            vec3 rotate(vec3 v) { return v + 2.0 * cross(rotation.xyz, cross(rotation.xyz, v) + rotation.w * v); }
            void main() {
                vec3 p = rotate(position); n = rotate(normal); c = color;
                // Compress along the world-space collision normal, preserving volume.
                vec3 axis = vec3(deformation.x, -deformation.y, 0.0);
                float squash = 1.0 - deformation.z;
                float stretch = inversesqrt(squash);
                p = p * stretch + axis * dot(p, axis) * (squash - stretch);
                // Inverse transpose keeps the lighting attached to the deformed surface.
                n = n / stretch + axis * dot(n, axis) * (1.0 / squash - 1.0 / stretch);
                vec2 point = center + vec2(p.x, -p.y) * radius;
                gl_Position = vec4(point.x / viewport.x * 2.0 - 1.0, 1.0 - point.y / viewport.y * 2.0, -p.z * 0.25, 1.0);
            }
        """
        private const val MeshFragment = """
            precision mediump float; varying vec3 n; varying vec3 c;
            void main() {
                vec3 normal = normalize(n); vec3 light = normalize(vec3(-0.5, 0.7, 1.2));
                float diffuse = max(dot(normal, light), 0.0);
                vec3 halfway = normalize(light + vec3(0.0, 0.0, 1.0));
                // Soft lighting for the runtime vertex-color representation.
                float specular = pow(max(dot(normal, halfway), 0.0), 12.0) * 0.065;
                vec3 linear = c * (0.38 + 0.62 * diffuse) + vec3(specular);
                gl_FragColor = vec4(pow(clamp(linear, 0.0, 1.0), vec3(1.0 / 2.2)), 1.0);
            }
        """
        private const val ShadowVertex = """
            attribute vec2 corner; uniform vec2 viewport; uniform vec2 center; uniform float radius;
            uniform vec3 deformation;
            varying vec2 uv;
            void main() { uv = corner;
                float squash = 1.0 - deformation.z;
                float stretch = inversesqrt(squash);
                vec2 offset = corner * stretch + deformation.xy * dot(corner, deformation.xy) * (squash - stretch);
                vec2 p = center + offset * radius;
                gl_Position = vec4(p.x / viewport.x * 2.0 - 1.0, 1.0 - p.y / viewport.y * 2.0, 0.0, 1.0); }
        """
        private const val ShadowFragment = """
            precision mediump float; varying vec2 uv;
            void main() { float r = length(uv); float alpha = (1.0 - smoothstep(0.4, 1.0, r)) * 0.26;
                gl_FragColor = vec4(0.0, 0.0, 0.0, alpha); }
        """
    }
}
