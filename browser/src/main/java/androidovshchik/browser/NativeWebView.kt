@file:Suppress("unused")

package androidovshchik.browser

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.*
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.parser.Tag
import timber.log.Timber
import java.io.IOException

class NativeWebView : FrameLayout, Callback, CoroutineScope {

    private val job = SupervisorJob()

    private val httpClient = OkHttpClient().apply {
        dispatcher().maxRequests = 4
    }

    private var webViewClient: BrowserClient? = null

    private var currentUrl: String? = null

    private var documentStyle = ""

    private var documentScript = ""

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
        webViewClient = BrowserClient().apply {
            init()
        }
    }

    fun loadUrl(url: String) {
        stopLoading()
        currentUrl = url
        makeRequest(url, url)
    }

    private fun makeRequest(url: String, tag: Any?) {
        val formattedUrl = when {
            url.trim().startsWith("//") -> "http://$url"
            else -> url.trim()
        }
        val request = Request.Builder()
            .url(formattedUrl)
            .tag(tag)
            .build()
        httpClient.newCall(request)
            .enqueue(this)
    }

    override fun onResponse(call: Call, response: Response) {
        val url = call.request()
            .url()
            .toString()
        if (!response.isSuccessful) {
            //webViewClient?.onReceivedError(url, response.code(), response.message())
            return
        }
        val tag = call.request()
            .tag()
        val body = response.body()
        launch {
            withContext(Dispatchers.IO) {
                when (body?.contentType()?.type()) {
                    "text" -> {
                        when (body.contentType()?.subtype()) {
                            "html" -> {
                                if (childCount >= 1) {
                                    return@withContext
                                }
                                val html = ElementViewGroup(context.applicationContext).apply {
                                    id = R.id.page_content
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    visibility = View.INVISIBLE
                                    setTag(Tag.valueOf("html"))
                                }
                                addView(html)
                                Jsoup.parse(body.string() ?: "").apply {
                                    //normalise()
                                    select("link")
                                        .forEach {
                                            if (it.attributes().hasKeyIgnoreCase("href")) {
                                                loadResource(it.attributes().getIgnoreCase("href"), tag)
                                            }
                                        }
                                    select("style")
                                        .forEach {
                                            documentStyle += it.data()
                                        }
                                    select("script")
                                        .forEach {
                                            if (it.attributes().hasKeyIgnoreCase("src")) {
                                                loadResource(it.attributes().getIgnoreCase("src"), tag)
                                            } else {
                                                documentScript += it.data()
                                            }
                                        }
                                }
                                Timber.d(documentStyle)
                                Timber.d(documentScript)
                            }
                            "css" -> {
                                Timber.d(body.string())
                            }
                        }
                    }
                    "application" -> {
                        when (body.contentType()?.subtype()) {
                            "javascript", "x-javascript" -> {
                                Timber.d(body.string())
                            }
                            "octet-stream" -> {

                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadResource(url: String, tag: Any?) {
        val formattedUrl = url.split("?")[0]
            .trim()
        if (".(css|js|eot|otf|ttf|woff|woff2)$".toRegex().matches(formattedUrl)) {
            makeRequest(formattedUrl, tag)
        }
    }

    override fun onFailure(call: Call, e: IOException) {
        /*if (call.isExecuted) {
            webViewClient?.onReceivedError(call.request()
                .url()
                .toString(), call., e.)
        }*/
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