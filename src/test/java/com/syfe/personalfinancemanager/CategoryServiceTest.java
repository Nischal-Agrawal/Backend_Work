package com.syfe.personalfinancemanager;

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
import com.syfe.personalfinancemanager.service.AuthService;
import com.syfe.personalfinancemanager.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("Unit test: getAllCategories combines default and custom categories")
    void testGetAllCategories() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        Category defaultCat = new Category("Salary", TransactionType.INCOME, false, null);
        Category customCat = new Category("Freelance", TransactionType.INCOME, true, user);

        when(categoryRepository.findByUserIsNull()).thenReturn(List.of(defaultCat));
        when(categoryRepository.findByUser(user)).thenReturn(List.of(customCat));

        CategoryListResponse resp = categoryService.getAllCategories();
        assertEquals(2, resp.getCategories().size());
        assertFalse(resp.getCategories().get(0).isCustom());
        assertTrue(resp.getCategories().get(1).isCustom());
    }

    @Test
    @DisplayName("Unit test: createCategory validates blank name and invalid type")
    void testCreateCategoryValidation() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        assertThrows(BadRequestException.class, () ->
                categoryService.createCategory(new CategoryRequest("  ", "INCOME")));

        assertThrows(BadRequestException.class, () ->
                categoryService.createCategory(new CategoryRequest("ValidName", "UNKNOWN_TYPE")));
    }

    @Test
    @DisplayName("Unit test: createCategory prevents duplicates")
    void testCreateCategoryDuplicate() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);
        when(categoryRepository.existsByUserIsNullAndNameIgnoreCase("Salary")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
                categoryService.createCategory(new CategoryRequest("Salary", "INCOME")));
    }

    @Test
    @DisplayName("Unit test: deleteCategory prevents deleting default categories")
    void testDeleteDefaultCategoryForbidden() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);
        when(categoryRepository.existsByUserIsNullAndNameIgnoreCase("Salary")).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> categoryService.deleteCategory("Salary"));
    }

    @Test
    @DisplayName("Unit test: deleteCategory throws ResourceNotFoundException if custom category not found")
    void testDeleteCategoryNotFound() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);
        when(categoryRepository.existsByUserIsNullAndNameIgnoreCase("NonExistent")).thenReturn(false);
        when(categoryRepository.findByUserAndNameIgnoreCase(user, "NonExistent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory("NonExistent"));
    }

    @Test
    @DisplayName("Unit test: deleteCategory prevents deleting category referenced by transactions")
    void testDeleteCategoryReferenced() {
        User user = new User("user@test.com", "pass", "User", "+123");
        Category cat = new Category("CustomExpense", TransactionType.EXPENSE, true, user);
        when(authService.getCurrentUser()).thenReturn(user);
        when(categoryRepository.existsByUserIsNullAndNameIgnoreCase("CustomExpense")).thenReturn(false);
        when(categoryRepository.findByUserAndNameIgnoreCase(user, "CustomExpense")).thenReturn(Optional.of(cat));
        when(transactionRepository.existsByUserAndCategoryIgnoreCase(user, "CustomExpense")).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.deleteCategory("CustomExpense"));
    }

    @Test
    @DisplayName("Unit test: deleteCategory deletes successfully when not referenced")
    void testDeleteCategorySuccess() {
        User user = new User("user@test.com", "pass", "User", "+123");
        Category cat = new Category("CustomExpense", TransactionType.EXPENSE, true, user);
        when(authService.getCurrentUser()).thenReturn(user);
        when(categoryRepository.existsByUserIsNullAndNameIgnoreCase("CustomExpense")).thenReturn(false);
        when(categoryRepository.findByUserAndNameIgnoreCase(user, "CustomExpense")).thenReturn(Optional.of(cat));
        when(transactionRepository.existsByUserAndCategoryIgnoreCase(user, "CustomExpense")).thenReturn(false);

        ApiResponse resp = categoryService.deleteCategory("CustomExpense");
        assertEquals("Category deleted successfully", resp.getMessage());
        verify(categoryRepository, times(1)).delete(cat);
    }
}
