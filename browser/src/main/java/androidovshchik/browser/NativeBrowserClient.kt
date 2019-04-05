@file:Suppress("unused")

package androidovshchik.browser

import android.graphics.Bitmap

private typealias ShouldOverrideUrlLoading = (url: String) -> Boolean

private typealias OnPageStarted = (url: String, favicon: Bitmap?) -> Unit

private typealias OnPageFinished = (url: String) -> Unit

private typealias OnReceivedError = (url: String, code: Int, description: String) -> Unit

class NativeBrowserClient {

    private var overrideUrlLoading: ShouldOverrideUrlLoading? = null

    private var pageStarted: OnPageStarted? = null

    private var pageFinished: OnPageFinished? = null

    private var receivedError: OnReceivedError? = null

    fun shouldOverrideUrlLoading(shouldOverrideUrlLoading: ShouldOverrideUrlLoading) {
        overrideUrlLoading = shouldOverrideUrlLoading
    }

    fun onPageStarted(onPageStarted: OnPageStarted) {
        pageStarted = onPageStarted
    }

    fun onPageFinished(onPageFinished: OnPageFinished) {
        pageFinished = onPageFinished
    }

    fun onReceivedError(onReceivedError: OnReceivedError) {
        receivedError = onReceivedError
    }

    fun shouldOverrideUrlLoading(url: String): Boolean {
        return overrideUrlLoading?.invoke(url) ?: false
    }

    fun onPageStarted(url: String, favicon: Bitmap?) {
        pageStarted?.invoke(url, favicon)
    }

    fun onPageFinished(url: String) {
        pageFinished?.invoke(url)
    }

    fun onReceivedError(url: String, code: Int, description: String) {
        receivedError?.invoke(url, code, description)
    }
}