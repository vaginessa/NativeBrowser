@file:Suppress("unused")

package androidovshchik.browser

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import okhttp3.*
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException

class NativeWebView : FrameLayout, Callback, CoroutineScope {

    private val job = SupervisorJob()

    private val httpClient = OkHttpClient().apply {
        dispatcher().maxRequests = 4
    }

    private var webViewClient: BrowserClient? = null

    private var currentUrl: String? = null

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
        val request = Request.Builder()
            .url(url)
            .tag(url)
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
        val tag = call.request().tag()
        val body = response.body()
        when (body?.contentType()?.type()) {
            "text" -> {
                when (body.contentType()?.subtype()) {
                    "html" -> {
                        //if () {

                        //}
                        Jsoup.parse(body.string() ?: "").apply {
                            //normalise()
                            select("link")
                                .forEach {
                                    Timber.d("link: " + it.attributes().asList())
                                    if (!it.attributes().hasKeyIgnoreCase("href")) {
                                        return@forEach
                                    }
                                    Timber.d("href: " + it.attributes().getIgnoreCase("href"))
                                    val request = Request.Builder()
                                        .url(it.attributes().getIgnoreCase("href"))
                                        .tag(tag)
                                        .build()
                                    httpClient.newCall(request)
                                        .enqueue(this@NativeWebView)
                                }
                            select("script")
                                .forEach {
                                    Timber.d("script: " + it.attributes().asList())
                                    if (!it.attributes().hasKeyIgnoreCase("src")) {
                                        return@forEach
                                    }
                                    Timber.d("src: " + it.attributes().getIgnoreCase("src"))
                                    val request = Request.Builder()
                                        .url(it.attributes().getIgnoreCase("src"))
                                        .tag(tag)
                                        .build()
                                    httpClient.newCall(request)
                                        .enqueue(this@NativeWebView)
                                }
                            /*head()
                                .select("style")
                                .forEach {
                                    it.data()
                                }*/
                        }
                        /*val body = ElementViewGroup(context).apply {
                            tag = Tag.valueOf("body")
                        }
                        addView(body)
                        body.smth(document.body())*/
                    }
                    else -> {
                        Timber.d(body?.contentType()?.type() + "/" + body?.contentType()?.subtype())
                    }
                }
            }
            else -> {
                Timber.d(body?.contentType()?.type() + "/" + body?.contentType()?.subtype())
            }
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