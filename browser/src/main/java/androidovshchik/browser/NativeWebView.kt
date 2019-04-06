@file:Suppress("unused")

package androidovshchik.browser

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Tag
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URI

open class NativeWebView : FrameLayout, CoroutineScope {

    protected val job = SupervisorJob()

    override val coroutineContext = Dispatchers.Main + job

    var browserClient: BrowserClient? = null

    protected var source = Source.HTML

    protected var input: Any = ""

    protected var styles = ""

    protected var scripts = ""

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

    fun setBrowserClient(init: BrowserClient.() -> Unit) {
        browserClient = BrowserClient().apply { init() }
    }

    fun loadHtml(html: String) {
        stopLoading()
        source = Source.HTML
        input = html
        load()
    }

    fun loadFromAssets(path: String) {
        stopLoading()
        source = Source.ASSETS
        input = path.trim()
            .replace("^\\/+".toRegex(), "")
        load()
    }

    fun loadFromRaw(id: Int) {
        stopLoading()
        source = Source.RAW
        input = id
        load()
    }

    fun loadFromFile(file: File) {
        stopLoading()
        source = Source.LOCAL
        input = file
        load()
    }

    fun loadFromUrl(url: String) {
        stopLoading()
        source = Source.REMOTE
        input = url
        load()
    }

    protected fun loadText(source: Source, input: Any): String? {
        try {
            return when (source) {
                Source.HTML -> input as String
                Source.ASSETS -> context.assets.open(input as String)
                    .bufferedReader()
                    .use { it.readText() }
                Source.RAW -> resources.openRawResource(input as Int)
                    .bufferedReader()
                    .use { it.readText() }
                Source.LOCAL -> ""
                Source.REMOTE -> ""
                /*
                makeRequest(url, url).await().body()
                when ("${response?.contentType()?.type()}/${response?.contentType()?.subtype()}".toLowerCase()) {
                    "text/html" -> {

                    }
                    else -> {
                        return@launch
                    }
                }*/
            }
        } catch (e: Exception) {
        }
        return null
    }

    @Throws(IOException::class)
    private fun loadRemoteResource(url: String, tag: Any?): Call? {
        var formattedUrl = url.split("?")[0]
            .trim()
        if (formattedUrl.startsWith("//")) {
            formattedUrl = "http:$formattedUrl"
        }
        val uri = URI(url)
        Timber.d(uri.toString())
        return null
        /*return if (".*.(css|js|eot|otf|ttf|woff|woff2)$".toRegex(setOf(RegexOption.IGNORE_CASE)).matches(formattedUrl)) {
            makeRequest(formattedUrl, tag)
        } else null*/
    }

    protected fun load() {
        launch {
            val html = loadText(source, input)
            val document = parseHtml(html)
            val htmlGroup = ElementGroup(context.applicationContext).apply {
                id = R.id.native_browser_html
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                tag = Tag.valueOf("html")
            }
            val bodyGroup = ElementGroup(context.applicationContext).apply {
                id = R.id.native_browser_body
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.MATCH_PARENT
                )
                tag = Tag.valueOf("body")
            }
            htmlGroup.addView(bodyGroup)
            addView(htmlGroup)
            bodyGroup.init(document.body())
        }
    }

    @Throws(IOException::class)
    protected fun makeRequest(url: String, tag: Any?): Call = httpClient.newCall(
        Request.Builder()
            .url(url)
            .tag(tag)
            .build()
    )

    protected suspend fun parseHtml(html: String?) = withContext(Dispatchers.IO) {
        return@withContext Jsoup.parse(html ?: "").apply {
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

    fun reload() {
        stopLoading()
        load()
    }

    fun stopLoading() {
        job.cancelChildren()
        styles = ""
        scripts = ""
    }

    fun destroy() {
        stopLoading()
    }

    enum class Source {
        HTML, ASSETS, RAW, LOCAL, REMOTE
    }

    companion object {

        private val httpClient = OkHttpClient().apply {
            dispatcher().maxRequests = 4
        }
    }
}