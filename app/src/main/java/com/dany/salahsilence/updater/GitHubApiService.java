package com.dany.salahsilence.updater;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface GitHubApiService {
    // Define the endpoint to get the latest release
    @GET("repos/{owner}/{repo}/releases/latest")
    Call<GitHubRelease> getLatestRelease(
            @Path("owner") String owner,
            @Path("repo") String repo
    );
} 