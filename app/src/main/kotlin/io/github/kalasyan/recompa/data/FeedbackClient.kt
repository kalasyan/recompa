package io.github.kalasyan.recompa.data

import io.github.kalasyan.recompa.BuildConfig
import io.github.kalasyan.recompa.core.catalog.LiveReports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What happened to a report or suggestion sent without a GitHub account. */
sealed interface FeedbackResult {
    data object Sent : FeedbackResult
    data object TooMany : FeedbackResult
    data object RepoNotFound : FeedbackResult
    data object Failed : FeedbackResult
}

/**
 * Sends compatibility reports and port suggestions to the catalog's feedback endpoint
 * (`feedback_url` in `index.json`), which opens the GitHub issue on the player's behalf,
 * and reads the reports as they stand now.
 */
class FeedbackClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    /** [result] is `great`, `playable` or `broken`. */
    suspend fun sendReport(port: String, device: String, version: String?, result: String, notes: String) =
        post(
            "report",
            JSONObject()
                .put("port", port)
                .put("device", device)
                .put("version", version ?: "")
                .put("result", result)
                .put("notes", notes),
        )

    suspend fun sendSuggestion(repo: String, game: String, notes: String) =
        post("suggestion", JSONObject().put("repo", repo).put("game", game).put("notes", notes))

    /** The reports as the endpoint counts them now, or null when it can't be reached. */
    suspend fun reports(): LiveReports? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/reports")
            .header("User-Agent", "Recompa/${BuildConfig.VERSION_NAME}")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                LiveReports.parse(response.body?.string() ?: return@withContext null)
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null // malformed answer: kotlinx.serialization's errors extend it
        }
    }

    private suspend fun post(path: String, body: JSONObject): FeedbackResult = withContext(Dispatchers.IO) {
        body.put("app", BuildConfig.VERSION_NAME)
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/$path")
            .header("User-Agent", "Recompa/${BuildConfig.VERSION_NAME}")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> FeedbackResult.Sent
                    response.code == 429 -> FeedbackResult.TooMany
                    response.code == 422 && "repo_not_found" in (response.body?.string() ?: "") ->
                        FeedbackResult.RepoNotFound
                    else -> FeedbackResult.Failed
                }
            }
        } catch (e: IOException) {
            FeedbackResult.Failed
        }
    }
}
