package com.syfe.personalfinancemanager.controller;

import com.syfe.personalfinancemanager.dto.request.CategoryRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.CategoryListResponse;
import com.syfe.personalfinancemanager.dto.response.CategoryResponse;
import com.syfe.personalfinancemanager.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller handling category operations (default and custom categories).
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Lists all categories accessible to the authenticated user.
     *
     * @return 200 OK with list of categories.
     */
    @GetMapping
    public ResponseEntity<CategoryListResponse> getAllCategories() {
        CategoryListResponse response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a custom category.
     *
     * @param request Category creation payload.
     * @return 201 Created with created category.
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Deletes a custom category by name.
     *
     * @param name Name of category to delete.
     * @return 200 OK with success message.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<ApiResponse> deleteCategory(@PathVariable("name") String name) {
        ApiResponse response = categoryService.deleteCategory(name);
        return ResponseEntity.ok(response);
    }
}
