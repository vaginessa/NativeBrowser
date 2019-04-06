@file:Suppress("unused")

package androidovshchik.browser.extensions

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {

        override fun onResponse(call: Call, response: Response) {
            if (!continuation.isCancelled) {
                continuation.resume(response)
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) {
                continuation.resumeWithException(e)
            }
        }
    })
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (e: Throwable) {
        }
    }
}