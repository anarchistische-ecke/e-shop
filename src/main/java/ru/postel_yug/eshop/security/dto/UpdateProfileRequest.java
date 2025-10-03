package ru.postel_yug.eshop.security.dto;

public class UpdateProfileRequest {
    private String name;  // the new name for the user (profile update)

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

