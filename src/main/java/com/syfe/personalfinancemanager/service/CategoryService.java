package com.syfe.personalfinancemanager.service;

import com.syfe.personalfinancemanager.dto.request.CategoryRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.CategoryListResponse;
import com.syfe.personalfinancemanager.dto.response.CategoryResponse;
import com.syfe.personalfinancemanager.entity.Category;
import com.syfe.personalfinancemanager.entity.TransactionType;
import com.syfe.personalfinancemanager.entity.User;
import com.syfe.personalfinancemanager.exception.BadRequestException;
import com.syfe.personalfinancemanager.exception.ConflictException;
import com.syfe.personalfinancemanager.exception.ForbiddenException;
import com.syfe.personalfinancemanager.exception.ResourceNotFoundException;
import com.syfe.personalfinancemanager.repository.CategoryRepository;
import com.syfe.personalfinancemanager.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service managing system default categories and user custom categories.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AuthService authService;

    private static final Map<String, TransactionType> DEFAULT_CATEGORIES = Map.of(
            "Salary", TransactionType.INCOME,
            "Food", TransactionType.EXPENSE,
            "Rent", TransactionType.EXPENSE,
            "Transportation", TransactionType.EXPENSE,
            "Entertainment", TransactionType.EXPENSE,
            "Healthcare", TransactionType.EXPENSE,
            "Utilities", TransactionType.EXPENSE
    );

    public CategoryService(CategoryRepository categoryRepository,
                           TransactionRepository transactionRepository,
                           AuthService authService) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.authService = authService;
    }

    /**
     * Seeds system default categories idempotently upon initialization.
     */
    @PostConstruct
    @Transactional
    public void initDefaultCategories() {
        for (Map.Entry<String, TransactionType> entry : DEFAULT_CATEGORIES.entrySet()) {
            if (!categoryRepository.existsByUserIsNullAndNameIgnoreCase(entry.getKey())) {
                Category defaultCategory = new Category(entry.getKey(), entry.getValue(), false, null);
                categoryRepository.save(defaultCategory);
            }
        }
    }

    /**
     * Retrieves all categories accessible to the current user (default + custom).
     *
     * @return List of Category responses.
     */
    @Transactional(readOnly = true)
    public CategoryListResponse getAllCategories() {
        User currentUser = authService.getCurrentUser();
        List<Category> defaults = categoryRepository.findByUserIsNull();
        List<Category> customs = categoryRepository.findByUser(currentUser);

        List<CategoryResponse> list = new ArrayList<>();
        for (Category c : defaults) {
            list.add(new CategoryResponse(c.getName(), c.getType().name(), false));
        }
        for (Category c : customs) {
            list.add(new CategoryResponse(c.getName(), c.getType().name(), true));
        }

        return new CategoryListResponse(list);
    }

    /**
     * Creates a custom category for the authenticated user.
     *
     * @param request Custom category creation request.
     * @return Created category response.
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        User currentUser = authService.getCurrentUser();
        String name = request.getName().trim();
        if (name.isBlank()) {
            throw new BadRequestException("Category name cannot be blank");
        }

        TransactionType type;
        try {
            type = TransactionType.valueOf(request.getType().trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid category type. Must be INCOME or EXPENSE");
        }

        // Duplicate checks
        if (categoryRepository.existsByUserIsNullAndNameIgnoreCase(name) ||
                categoryRepository.existsByUserAndNameIgnoreCase(currentUser, name)) {
            throw new ConflictException("Category with name '" + name + "' already exists");
        }

        Category category = new Category(name, type, true, currentUser);
        categoryRepository.save(category);

        return new CategoryResponse(category.getName(), category.getType().name(), true);
    }

    /**
     * Deletes a custom category by name for the authenticated user.
     *
     * @param categoryName Name of category to delete.
     * @return Success API response.
     */
    @Transactional
    public ApiResponse deleteCategory(String categoryName) {
        User currentUser = authService.getCurrentUser();
        String decodedName = URLDecoder.decode(categoryName, StandardCharsets.UTF_8).trim();

        // Check if default category
        if (categoryRepository.existsByUserIsNullAndNameIgnoreCase(decodedName)) {
            throw new ForbiddenException("Default categories cannot be deleted");
        }

        Category category = categoryRepository.findByUserAndNameIgnoreCase(currentUser, decodedName)
                .orElseThrow(() -> new ResourceNotFoundException("Category '" + decodedName + "' not found"));

        // Check if referenced by existing transactions
        if (transactionRepository.existsByUserAndCategoryIgnoreCase(currentUser, category.getName())) {
            throw new ConflictException("Category '" + category.getName() + "' is referenced by existing transactions and cannot be deleted");
        }

        categoryRepository.delete(category);
        return new ApiResponse("Category deleted successfully");
    }

    /**
     * Validates and retrieves an accessible category for a user.
     *
     * @param user The user.
     * @param categoryName The name of the category.
     * @return The Category entity.
     */
    @Transactional(readOnly = true)
    public Category validateAndGetAccessibleCategory(User user, String categoryName) {
        String trimmed = categoryName.trim();
        Optional<Category> custom = categoryRepository.findByUserAndNameIgnoreCase(user, trimmed);
        if (custom.isPresent()) {
            return custom.get();
        }

        Optional<Category> defaultCat = categoryRepository.findByUserIsNullAndNameIgnoreCase(trimmed);
        if (defaultCat.isPresent()) {
            return defaultCat.get();
        }

        throw new BadRequestException("Category '" + categoryName + "' does not exist or is not accessible");
    }
}
