package com.theo.community_api.image.domain;

public enum ImageCategory {
    PROFILE("profiles"),
    POST("posts");

    private final String directory;

    ImageCategory(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}