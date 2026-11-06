package capital.cannuran.pulse.data

import capital.cannuran.pulse.core.HttpClient
import capital.cannuran.pulse.core.HttpResult
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** Blocking HttpURLConnection transport. Call from a background thread (the worker does). */
class AndroidHttp(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 150_000, // web-search turns can take a while
) : HttpClient {

    override fun execute(url: String, headers: Map<String, String>, body: String?): HttpResult {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            } else {
                conn.requestMethod = "GET"
            }
            val status = conn.responseCode
            val stream: InputStream? = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return HttpResult(status, text)
        } finally {
            conn.disconnect()
        }
    }
}
