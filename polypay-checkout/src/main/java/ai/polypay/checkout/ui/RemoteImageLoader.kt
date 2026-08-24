package ai.polypay.checkout.ui

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Loads optional merchant avatars without adding or configuring a host image-library singleton. */
internal object RemoteImageLoader {
    private const val MAX_IMAGE_BYTES = 2_000_000
    private val executor = Executors.newCachedThreadPool()

    /** Loads a bounded HTTPS image while preserving the current brand placeholder on failure. */
    fun load(imageView: ImageView, imageUrl: String?) {
        val url = validatedUrl(imageUrl) ?: return
        executor.execute {
            val bitmap = runCatching { download(url) }.getOrNull() ?: return@execute
            Handler(Looper.getMainLooper()).post { imageView.setImageBitmap(bitmap) }
        }
    }

    /** Accepts only credential-free HTTPS image URLs returned by the checkout API. */
    private fun validatedUrl(value: String?): URL? {
        val uri = runCatching { URI(value.orEmpty()) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || uri.host.isNullOrBlank()) return null
        return runCatching { uri.toURL() }.getOrNull()
    }

    /** Downloads at most the configured number of bytes without following redirects. */
    private fun download(url: URL): android.graphics.Bitmap? {
        val connection = url.openConnection() as? HttpsURLConnection ?: return null
        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "image/*")
        try {
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLengthLong > MAX_IMAGE_BYTES) return null
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            connection.disconnect()
        }
    }
}
