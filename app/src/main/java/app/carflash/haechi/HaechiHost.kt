package app.carflash.haechi

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import app.carflash.vehicle.VehicleGateway.Companion.log

/** 해치 상태. 와이어프레임 v0.3의 화면별 배치와 같다. */
enum class HaechiState(val js: String) { IDLE("idle"), HELLO("hello"), DANCE("dance"), STOP("stop"), CHEER("cheer"), WAIT("wait"), REST("rest") }

/**
 * 앱 전체에서 WebView 하나만 쓴다(three.js를 화면마다 다시 읽지 않도록).
 * 화면이 바뀌면 같은 WebView를 새 자리로 옮겨 붙인다.
 */
class HaechiHost(private val context: Context) {
    private var web: WebView? = null
    private var ready = false
    private val pending = ArrayList<String>()
    var lastFps: Int = 0
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun view(): WebView {
        web?.let { w -> (w.parent as? ViewGroup)?.removeView(w); return w }
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        val w = WebView(context)
        w.setBackgroundColor(Color.TRANSPARENT)
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false
        w.settings.mediaPlaybackRequiresUserGesture = true
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.isFocusable = false
        w.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                log("HAECHI js ${m.messageLevel()} ${m.message()}")
                return true
            }
        }
        w.addJavascriptInterface(Bridge(), "HaechiBridge")
        w.loadUrl("https://appassets.androidplatform.net/assets/haechi/index.html")
        web = w
        return w
    }

    private inner class Bridge {
        @JavascriptInterface fun onReady(ok: Boolean) {
            log("HAECHI ready ok=$ok")
            web?.post {
                ready = ok
                pending.forEach { web?.evaluateJavascript(it, null) }
                pending.clear()
            }
        }
        @JavascriptInterface fun onFps(fps: Int) { lastFps = fps; log("HAECHI fps=$fps") }
    }

    private fun js(code: String) {
        val w = web ?: return
        w.post { if (ready) w.evaluateJavascript(code, null) else pending.add(code) }
    }

    fun setState(state: HaechiState, bpm: Int? = null) {
        log("HAECHI state=${state.js} bpm=$bpm")
        js("window.haechi.setState('${state.js}', ${bpm ?: "null"})")
    }
    fun setPosition(sec: Double, playing: Boolean) = js("window.haechi.setPosition($sec, $playing)")
    fun setFraming(bust: Boolean) = js("window.haechi.setFraming('${if (bust) "bust" else "full"}')")
    fun setYaw(y: Float) = js("window.haechi.setYaw($y)")
}
