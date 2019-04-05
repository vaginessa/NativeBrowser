@file:Suppress("unused")

package androidovshchik.browser

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request

class NativeWebView : FrameLayout, CoroutineScope {

    private val job = SupervisorJob()

    private val httpClient = OkHttpClient()

    private var currentUrl: String? = null

    private var webViewClient: NativeBrowserClient? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        init()
    }

    private fun init() {

    }

    fun setWebViewClient(init: NativeBrowserClient.() -> Unit) {
        val client = NativeBrowserClient()
        client.init()
        webViewClient = client
    }

    fun loadUrl(url: String) {
        stopLoading()
        currentUrl = url
        launch {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .tag(url)
                    .build()
                val response = httpClient.newCall(request)
                    .execute()
                val body = response.body()
                when (body?.contentType()?.type()) {
                    "text" -> {
                        when (body.contentType()?.subtype()) {
                            "html" -> {
                                //Jsoup.parse()
                            }
                        }
                    }
                }
            }
        }
    }

    fun reload() {
        stopLoading()
        loadUrl(currentUrl ?: return)
    }

    fun stopLoading() {
        coroutineContext.cancelChildren()
        val dispatcher = httpClient.dispatcher()
        for (call in dispatcher.runningCalls()) {
            if (call.request().tag()?.equals(currentUrl) == true) {
                call.cancel()
            }
        }
        for (call in dispatcher.queuedCalls()) {
            if (call.request().tag()?.equals(currentUrl) == true) {
                call.cancel()
            }
        }
    }

    fun canGoBack(): Boolean {
        return false
    }

    fun goBack() {}

    fun destroy() {
        coroutineContext.cancelChildren()
        val dispatcher = httpClient.dispatcher()
        for (call in dispatcher.runningCalls()) {
            call.cancel()
        }
        for (call in dispatcher.queuedCalls()) {
            call.cancel()
        }
    }

    override val coroutineContext = Dispatchers.Main + job
}