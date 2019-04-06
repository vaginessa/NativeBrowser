@file:Suppress("unused")

package androidovshchik.browser

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidovshchik.browser.extensions.await
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
        //if (!response.isSuccessful) {
            //webViewClient?.onReceivedError(url, response.code(), response.message())
        // }
        var styles = ""
        var scripts = ""
        launch {
            val body = makeRequest(url, url).await(-1).body()
            val bodyType = "${body?.contentType()?.type()}/${body?.contentType()?.subtype()}".toLowerCase()
            when (bodyType) {
                "text/html" -> {
                    val html = ElementViewGroup(context.applicationContext).apply {
                        id = R.id.page_content
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        visibility = View.INVISIBLE
                        tag = Tag.valueOf("html")
                    }
                    addView(html)
                }
                else -> {
                    return@launch
                }
            }
            withContext(Dispatchers.IO) {
                val job = Job(job)
                Jsoup.parse(body?.string() ?: "").apply {
                    //normalise()
                    select("style")
                        .forEach {
                            styles += it.data()
                        }
                    var i = 0
                    select("link") + select("script")
                        .forEach {
                            when {
                                it.attributes().hasKeyIgnoreCase("href") or it.attributes().hasKeyIgnoreCase("src") -> {
                                    val resourceUrl = when {
                                        it.attributes().hasKeyIgnoreCase("href") -> it.attributes().getIgnoreCase("href")
                                        else -> it.attributes().getIgnoreCase("src")
                                    }
                                    val call = loadResource(resourceUrl, url) ?: return@forEach
                                    i++
                                    launch(job) {
                                        val resource = call.await(i).body()
                                        val resourceType =
                                            "${resource?.contentType()?.type()}/${resource?.contentType()?.subtype()}".toLowerCase()
                                        when (resourceType) {
                                            "text/css" -> {

                                            }
                                            "application/javascript", "application/x-javascript" -> {

                                            }
                                            "application/octet-stream" -> {

                                            }
                                        }
                                    }
                                }
                                else -> if (it.tagName() == "script") {
                                    scripts += it.data()
                                }
                            }
                        }
                }
                job.children.forEach {
                    it.join()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun loadResource(url: String, tag: Any?): Call? {
        var formattedUrl = url.split("?")[0]
            .trim()
        if (formattedUrl.startsWith("//")) {
            formattedUrl = "http:$formattedUrl"
        }
        if (".(css|js|eot|otf|ttf|woff|woff2)$".toRegex().matches(formattedUrl)) {
            return makeRequest(formattedUrl, tag)
        }
        return null
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
        job.cancelChildren()
    }

    fun destroy() {
        stopLoading()
    }

    override val coroutineContext = Dispatchers.Main + job
}