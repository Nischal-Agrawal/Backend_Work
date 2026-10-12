package com.syfe.personalfinancemanager.dto.response;

import java.util.List;

/**
 * Response DTO wrapper for list of categories.
 */
public class CategoryListResponse {

    private List<CategoryResponse> categories;

    public CategoryListResponse() {
    }

    public CategoryListResponse(List<CategoryResponse> categories) {
        this.categories = categories;
    }

    public List<CategoryResponse> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryResponse> categories) {
        this.categories = categories;
    }
}
