package com.dany.salahsilence.updater;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// Data class to represent the JSON response from GitHub API /releases/latest
public class GitHubRelease {
    @SerializedName("tag_name")
    public String tagName;

    @SerializedName("name")
    public String releaseName; // Release title

    @SerializedName("body")
    public String body; // Release notes

    @SerializedName("assets")
    public List<Asset> assets;

    public static class Asset {
        @SerializedName("name")
        public String name;

        @SerializedName("browser_download_url")
        public String downloadUrl;
    }
} 