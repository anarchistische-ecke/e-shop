package com.example.api.content;

public enum ContentAccessMode {
    PUBLISHED,
    PREVIEW;

    public boolean isPreview() {
        return this == PREVIEW;
    }
}
