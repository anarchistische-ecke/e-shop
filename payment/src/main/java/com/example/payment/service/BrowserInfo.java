package com.example.payment.service;

public class BrowserInfo {
    private String ip;
    private String acceptHeader;
    private boolean javascriptEnabled;
    private String language;
    private int screenHeight;
    private int screenWidth;
    private int timezoneOffset;
    private String userAgent;
    private boolean javaEnabled;
    private int colorDepth;

    public BrowserInfo() {}
    public BrowserInfo(String ip, String acceptHeader, boolean javascriptEnabled, String language,
                       int screenHeight, int screenWidth, int timezoneOffset,
                       String userAgent, boolean javaEnabled, int colorDepth) {
        this.ip = ip;
        this.acceptHeader = acceptHeader;
        this.javascriptEnabled = javascriptEnabled;
        this.language = language;
        this.screenHeight = screenHeight;
        this.screenWidth = screenWidth;
        this.timezoneOffset = timezoneOffset;
        this.userAgent = userAgent;
        this.javaEnabled = javaEnabled;
        this.colorDepth = colorDepth;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getAcceptHeader() {
        return acceptHeader;
    }

    public void setAcceptHeader(String acceptHeader) {
        this.acceptHeader = acceptHeader;
    }

    public boolean isJavascriptEnabled() {
        return javascriptEnabled;
    }

    public void setJavascriptEnabled(boolean javascriptEnabled) {
        this.javascriptEnabled = javascriptEnabled;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public int getTimezoneOffset() {
        return timezoneOffset;
    }

    public void setTimezoneOffset(int timezoneOffset) {
        this.timezoneOffset = timezoneOffset;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isJavaEnabled() {
        return javaEnabled;
    }

    public void setJavaEnabled(boolean javaEnabled) {
        this.javaEnabled = javaEnabled;
    }

    public int getColorDepth() {
        return colorDepth;
    }

    public void setColorDepth(int colorDepth) {
        this.colorDepth = colorDepth;
    }
}

