package com.example.smartintersections.api;

import com.example.smartintersections.model.IntersectionResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenDataBordeauxApi {

    @GET("geojson")
    Call<IntersectionResponse> getIntersections(
        @Query("key") String apiKey,
        @Query("dataset") String dataset
    );
}
