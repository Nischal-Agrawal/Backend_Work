package com.syfe.personalfinancemanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syfe.personalfinancemanager.dto.request.CategoryRequest;
import com.syfe.personalfinancemanager.dto.request.LoginRequest;
import com.syfe.personalfinancemanager.dto.request.RegisterRequest;
import com.syfe.personalfinancemanager.dto.request.TransactionRequest;
import com.syfe.personalfinancemanager.repository.CategoryRepository;
import com.syfe.personalfinancemanager.repository.GoalRepository;
import com.syfe.personalfinancemanager.repository.TransactionRepository;
import com.syfe.personalfinancemanager.repository.UserRepository;
import com.syfe.personalfinancemanager.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryService categoryService;

    private MockHttpSession userSessionA;
    private MockHttpSession userSessionB;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        goalRepository.deleteAll();
        categoryRepository.findAll().stream().filter(c -> c.isCustom()).forEach(categoryRepository::delete);
        userRepository.deleteAll();
        categoryService.initDefaultCategories();

        // Register & login User A
        RegisterRequest regA = new RegisterRequest("usera@example.com", "password123", "User A", "+1000000001");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regA)));
        MvcResult resA = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("usera@example.com", "password123")))).andReturn();
        userSessionA = (MockHttpSession) resA.getRequest().getSession(false);

        // Register & login User B
        RegisterRequest regB = new RegisterRequest("userb@example.com", "password123", "User B", "+1000000002");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regB)));
        MvcResult resB = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("userb@example.com", "password123")))).andReturn();
        userSessionB = (MockHttpSession) resB.getRequest().getSession(false);
    }

    @Test
    @DisplayName("Lists all 7 default categories initially")
    void testGetDefaultCategories() throws Exception {
        mockMvc.perform(get("/api/categories").session(userSessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(greaterThanOrEqualTo(7))))
                .andExpect(jsonPath("$.categories[?(@.name == 'Salary')].isCustom", contains(false)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Salary')].type", contains("INCOME")))
                .andExpect(jsonPath("$.categories[?(@.name == 'Food')].isCustom", contains(false)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Rent')].isCustom", contains(false)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Transportation')].isCustom", contains(false)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Entertainment')].isCustom", contains(false)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Healthcare')].isCustom", contains(false)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Utilities')].isCustom", contains(false)));
    }

    @Test
    @DisplayName("Creates custom category successfully")
    void testCreateCustomCategory() throws Exception {
        CategoryRequest req = new CategoryRequest("SideBusinessIncome", "INCOME");

        mockMvc.perform(post("/api/categories")
                        .session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("SideBusinessIncome")))
                .andExpect(jsonPath("$.type", is("INCOME")))
                .andExpect(jsonPath("$.isCustom", is(true)));
    }

    @Test
    @DisplayName("Rejects duplicate category matching default category")
    void testRejectDuplicateDefaultCategory() throws Exception {
        CategoryRequest req = new CategoryRequest("Salary", "INCOME");

        mockMvc.perform(post("/api/categories")
                        .session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Rejects duplicate category matching existing custom category")
    void testRejectDuplicateCustomCategory() throws Exception {
        CategoryRequest req = new CategoryRequest("Consulting", "INCOME");

        mockMvc.perform(post("/api/categories").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Rejects invalid category type with 400 Bad Request")
    void testRejectInvalidCategoryType() throws Exception {
        CategoryRequest req = new CategoryRequest("BadCategory", "INVALID_TYPE");

        mockMvc.perform(post("/api/categories").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Prohibits deleting default categories with 403 Forbidden")
    void testCannotDeleteDefaultCategory() throws Exception {
        mockMvc.perform(delete("/api/categories/Salary").session(userSessionA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("Deletes custom category successfully")
    void testDeleteCustomCategory() throws Exception {
        CategoryRequest req = new CategoryRequest("TempExpense", "EXPENSE");
        mockMvc.perform(post("/api/categories").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/TempExpense").session(userSessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Category deleted successfully")));
    }

    @Test
    @DisplayName("Rejects deleting custom category referenced by transactions with 409 Conflict")
    void testCannotDeleteReferencedCategory() throws Exception {
        CategoryRequest catReq = new CategoryRequest("Gym", "EXPENSE");
        mockMvc.perform(post("/api/categories").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catReq)))
                .andExpect(status().isCreated());

        TransactionRequest txReq = new TransactionRequest(
                BigDecimal.valueOf(50.00),
                LocalDate.now(),
                "Gym",
                "Monthly Gym Membership"
        );
        mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txReq)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/Gym").session(userSessionA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Ensures custom categories are isolated between users")
    void testUserCategoryIsolation() throws Exception {
        CategoryRequest req = new CategoryRequest("UserAPrivateCategory", "INCOME");
        mockMvc.perform(post("/api/categories").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // User B should not see User A's custom category
        mockMvc.perform(get("/api/categories").session(userSessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.name == 'UserAPrivateCategory')]", hasSize(0)));

        // User B cannot delete User A's custom category
        mockMvc.perform(delete("/api/categories/UserAPrivateCategory").session(userSessionB))
                .andExpect(status().isNotFound());
    }
}
