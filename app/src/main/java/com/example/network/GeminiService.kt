package com.example.network

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Moshi Mapped Request/Response Data Classes for Gemini REST API ---

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Retrofit Client Singleton ---

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

// --- API Usage Helper ---

object GeminiWeatherClient {
    suspend fun fetchEmergencyAdvisory(district: String, weatherStatus: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getFallbackAdvisory(district, weatherStatus)
        }

        val prompt = """
            You are a meteorological AI assistant for the "Live Weather Alert" app in Bangladesh.
            Given the Location/District: "$district" and current condition/status: "$weatherStatus".
            Provide a short, crisp safety advisory in 3 bullet points (max 50 words altogether).
            Keep it actionable and helpful (e.g. secure boats in coastal Bhola/Cox's Bazar, stay indoors in Dhaka).
            Do not output markdown headings, just the bullet points.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: getFallbackAdvisory(district, weatherStatus)
        } catch (e: Exception) {
            getFallbackAdvisory(district, weatherStatus)
        }
    }

    private fun getFallbackAdvisory(district: String, weatherStatus: String): String {
        return when {
            weatherStatus.contains("Cyclone", true) || weatherStatus.contains("Storm", true) -> {
                "• Avoid water bodies and coastal banks in $district.\n" +
                "• Secure roofing, livestock, and move to nearest Cyclone Shelter.\n" +
                "• Keep emergency dry rations and a battery-powered radio charged."
            }
            weatherStatus.contains("Rain", true) || weatherStatus.contains("Drizzle", true) -> {
                "• Watch for waterlogging or mudslides in hilly regions of $district.\n" +
                "• Reduce travel speed due to extremely low road visibility.\n" +
                "• Carry an umbrella and keep electric devices inside dry cases."
            }
            else -> {
                "• Weather is relatively peaceful in $district today.\n" +
                "• Keep hydrated if temperature rises above 32°C.\n" +
                "• Perfect day for local travel and capturing snapshots for the community."
            }
        }
    }
}
