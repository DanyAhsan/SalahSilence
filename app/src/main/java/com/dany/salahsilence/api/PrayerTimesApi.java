package com.dany.salahsilence.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PrayerTimesApi {
    @GET("v1/timings")
    Call<PrayerTimesResponse> getPrayerTimes(
        @Query("latitude") double latitude,
        @Query("longitude") double longitude,
        @Query("method") int method
    );
} 