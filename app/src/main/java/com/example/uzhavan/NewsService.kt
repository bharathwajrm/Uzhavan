package com.example.uzhavan

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ── DataStore ────────────────────────────────────────────────────────────────
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "uzhavan_prefs")

object PrefKeys {
    val NEWS_LANGUAGE = stringPreferencesKey("news_language")
    val DARK_MODE = stringPreferencesKey("dark_mode") // "light" | "dark" | "system"
}

// ── Models ───────────────────────────────────────────────────────────────────
data class NewsArticle(
    val title: String = "",
    val description: String = "",
    val url: String = "",
    val image: String? = null,
    val publishedAt: String = "",
    val source: NewsSource = NewsSource(),
    val newsSection: String = ""  // "Tamil Nadu" | "India" | "World"
)

data class NewsSource(val name: String = "")

data class GuardianResponse(
    @SerializedName("response") val response: GuardianResult = GuardianResult()
)

data class GuardianResult(
    @SerializedName("currentPage") val currentPage: Int = 1,
    @SerializedName("pages") val pages: Int = 1,
    @SerializedName("results") val results: List<GuardianArticle> = emptyList()
)

data class GuardianArticle(
    @SerializedName("webTitle") val webTitle: String = "",
    @SerializedName("webUrl") val webUrl: String = "",
    @SerializedName("webPublicationDate") val webPublicationDate: String = "",
    @SerializedName("sectionName") val sectionName: String = "",
    @SerializedName("fields") val fields: GuardianFields? = null
)

data class GuardianFields(
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("trailText") val trailText: String? = null
)

fun GuardianArticle.toNewsArticle(sectionLabel: String) = NewsArticle(
    title = webTitle,
    description = fields?.trailText?.replace(Regex("<[^>]*>"), "") ?: "",
    url = webUrl,
    image = fields?.thumbnail,
    publishedAt = webPublicationDate,
    source = NewsSource(name = sectionName),
    newsSection = sectionLabel
)

// ── Agriculture keyword filter ────────────────────────────────────────────────
private val AGRI_KEYWORDS = setOf(
    "agriculture", "farming", "farmer", "farmers", "crop", "crops", "harvest",
    "irrigation", "paddy", "rice", "wheat", "sugarcane", "cotton", "maize",
    "fertilizer", "pesticide", "livestock", "cattle", "poultry", "dairy",
    "soil", "drought", "monsoon", "sowing", "cultivation", "horticulture",
    "agri", "food security", "rural", "village", "kisan", "rabi", "kharif"
)

private fun String.isAgriRelated(): Boolean {
    val lower = lowercase()
    return AGRI_KEYWORDS.any { lower.contains(it) }
}

// ── News sections ─────────────────────────────────────────────────────────────
enum class NewsSection(val label: String, val query: String, val section: String?) {
    TAMIL_NADU("Tamil Nadu", "\"Tamil Nadu\" agriculture OR farming OR crops OR farmers OR irrigation OR harvest", null),
    INDIA("India", "India agriculture OR farming OR crops OR farmers OR irrigation OR harvest OR kisan", null),
    WORLD("World", "agriculture farming crops", "environment")
}

// ── Retrofit API ─────────────────────────────────────────────────────────────
interface GuardianApi {
    @GET("search")
    suspend fun searchNews(
        @Query("q") query: String,
        @Query("section") section: String?,
        @Query("order-by") orderBy: String = "newest",
        @Query("page-size") pageSize: Int = 20,
        @Query("page") page: Int = 1,
        @Query("show-fields") showFields: String = "thumbnail,trailText",
        @Query("api-key") apiKey: String = "test"
    ): GuardianResponse
}

// ── Repository ───────────────────────────────────────────────────────────────
object NewsRepository {
    private const val BASE_URL = "https://content.guardianapis.com/"

    private val api: GuardianApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GuardianApi::class.java)

    // page index per section
    private val sectionPages = mutableMapOf(
        NewsSection.TAMIL_NADU to 1,
        NewsSection.INDIA to 1,
        NewsSection.WORLD to 1
    )
    private val sectionHasMore = mutableMapOf(
        NewsSection.TAMIL_NADU to true,
        NewsSection.INDIA to true,
        NewsSection.WORLD to true
    )

    fun resetPages() {
        NewsSection.values().forEach { sectionPages[it] = 1; sectionHasMore[it] = true }
    }

    val hasMore: Boolean get() = sectionHasMore.values.any { it }

    suspend fun fetchAgriNews(page: Int = 1): Result<Pair<List<NewsArticle>, Boolean>> {
        if (page == 1) resetPages()
        // Pick the section that still has pages, in priority order: TN → India → World
        val section = NewsSection.values().firstOrNull { sectionHasMore[it] == true }
            ?: return Result.success(emptyList<NewsArticle>() to false)

        val sectionPage = sectionPages[section]!!
        return try {
            val resp = api.searchNews(
                query = section.query,
                section = section.section,
                page = sectionPage
            )
            val result = resp.response
            val articles = result.results
                .map { it.toNewsArticle(section.label) }
                .filter { (it.title + " " + it.description).isAgriRelated() }
            val more = result.currentPage < result.pages
            sectionHasMore[section] = more
            sectionPages[section] = sectionPage + 1
            Result.success(articles to hasMore)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
