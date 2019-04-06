@file:Suppress("unused")

package androidovshchik.browser

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidovshchik.browser.extensions.await
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Tag
import java.io.IOException

class NativeWebView : FrameLayout, CoroutineScope {

    private val job = SupervisorJob()

    private val httpClient = OkHttpClient().apply {
        dispatcher().maxRequests = 4
    }

    private var currentUrl: String? = null

    var webViewClient: BrowserClient? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    fun setWebViewClient(init: BrowserClient.() -> Unit) {
        webViewClient = BrowserClient().apply { init() }
    }

    fun loadUrl(url: String) {
        stopLoading()
        currentUrl = url
        var styles = ""
        var scripts = ""
        launch {
            val response = makeRequest(url, url).await().body()
            when ("${response?.contentType()?.type()}/${response?.contentType()?.subtype()}".toLowerCase()) {
                "text/html" -> {

                }
                else -> {
                    return@launch
                }
            }
            val document = withContext(Dispatchers.IO) {
                return@withContext Jsoup.parse(response?.string() ?: "").apply {
                    select("style")
                        .forEach {
                            styles += "${it.data()}\n"
                        }
                    select("link[href]")
                        .forEach {
                            val call = loadResource(it.attributes().getIgnoreCase("href"), url) ?: return@forEach
                            launch {
                                val resource = call.await().body()
                                when ("${resource?.contentType()?.type()}/${resource?.contentType()?.subtype()}".toLowerCase()) {
                                    "text/css" -> {
                                        styles += "${resource?.string() ?: ""}\n"
                                    }
                                    "application/octet-stream" -> {

                                    }
                                }
                            }
                        }
                    select("script")
                        .forEach {
                            if (!it.attributes().hasKeyIgnoreCase("src")) {
                                scripts += "${it.data()}\n"
                                return@forEach
                            }
                            val call = loadResource(it.attributes().getIgnoreCase("src"), url) ?: return@forEach
                            launch {
                                val resource = call.await().body()
                                when ("${resource?.contentType()?.type()}/${resource?.contentType()?.subtype()}".toLowerCase()) {
                                    "application/javascript", "application/x-javascript" -> {
                                        scripts += "${resource?.string() ?: ""}\n"
                                    }
                                }
                            }
                        }
                }
            }
            val html = ElementViewGroup(context.applicationContext).apply {
                id = R.id.native_browser_html
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                tag = Tag.valueOf("html")
            }
            val body = ElementViewGroup(context.applicationContext).apply {
                id = R.id.native_browser_body
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.MATCH_PARENT
                )
                tag = Tag.valueOf("body")
            }
            html.addView(body)
            addView(html)
            body.init(document.body())
        }
    }

    @Throws(IOException::class)
    private fun loadResource(url: String, tag: Any?): Call? {
        var formattedUrl = url.split("?")[0]
            .trim()
        if (formattedUrl.startsWith("//")) {
            formattedUrl = "http:$formattedUrl"
        }
        return if (".*.(css|js|eot|otf|ttf|woff|woff2)$".toRegex(setOf(RegexOption.IGNORE_CASE)).matches(formattedUrl)) {
            makeRequest(formattedUrl, tag)
        } else null
    }

    @Throws(IOException::class)
    private fun makeRequest(url: String, tag: Any?): Call {
        val request = Request.Builder()
            .url(url)
            .tag(tag)
            .build()
        return httpClient.newCall(request)
    }

    fun reload() {
        stopLoading()
        loadUrl(currentUrl ?: return)
    }

    fun stopLoading() {
        coroutineContext.cancelChildren()
    }

    fun destroy() {
        stopLoading()
    }

    override val coroutineContext = Dispatchers.Main + job
}