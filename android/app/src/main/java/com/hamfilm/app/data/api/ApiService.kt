package com.hamfilm.app.data.api

import com.hamfilm.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ---------- احراز هویت (نام کاربری + رمز) ----------
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun me(): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    // ---------- تنظیمات ----------
    @GET("api/settings/public")
    suspend fun publicSettings(): Response<PublicSettings>

    // ---------- فیلم‌ها ----------
    @GET("api/movies")
    suspend fun movies(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 24,
        @Query("genre") genre: String? = null,
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null
    ): Response<MoviesResponse>

    @GET("api/movies/featured")
    suspend fun featuredMovies(): Response<FeaturedResponse>

    @GET("api/movies/genres")
    suspend fun genres(): Response<GenresResponse>

    @GET("api/movies/{slug}")
    suspend fun movie(@Path("slug") slug: String): Response<MovieResponse>

    // ---------- پلن‌ها و اشتراک ----------
    @GET("api/plans")
    suspend fun plans(): Response<PlansResponse>

    @GET("api/subscriptions/me")
    suspend fun mySubscription(): Response<SubscriptionResponse>

    @POST("api/subscriptions/checkout")
    suspend fun checkout(@Body body: CheckoutRequest): Response<CheckoutResponse>

    // ---------- اتاق‌ها ----------
    @POST("api/rooms")
    suspend fun createRoom(@Body body: CreateRoomRequest): Response<RoomInfo>

    @GET("api/rooms/{code}")
    suspend fun roomInfo(@Path("code") code: String): Response<RoomInfo>

    // ---------- گزارش ----------
    @POST("api/reports")
    suspend fun report(@Body body: ReportRequest): Response<Unit>

    // ---------- تیکت‌ها (بک‌اند: messages به‌جای replies) ----------
    @GET("api/support/tickets")
    suspend fun tickets(): Response<TicketsResponse>

    @POST("api/support/tickets")
    suspend fun createTicket(@Body body: CreateTicketRequest): Response<TicketCreateResponse>

    @GET("api/support/tickets/{id}/messages")
    suspend fun ticketDetail(@Path("id") id: String): Response<TicketMessagesResponse>

    @POST("api/support/tickets/{id}/messages")
    suspend fun replyTicket(@Path("id") id: String, @Body body: ReplyTicketRequest): Response<Unit>
}
