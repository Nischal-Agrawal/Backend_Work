package com.syfe.personalfinancemanager.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO representing a category.
 */
public class CategoryResponse {

    private String name;
    private String type;

    @JsonProperty("isCustom")
    private boolean isCustom;

    public CategoryResponse() {
    }

    public CategoryResponse(String name, String type, boolean isCustom) {
        this.name = name;
        this.type = type;
        this.isCustom = isCustom;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }
}
