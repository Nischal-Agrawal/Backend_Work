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
class ReportIntegrationTest {

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

        RegisterRequest regA = new RegisterRequest("reportuser_a@example.com", "password123", "User A", "+1000000001");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regA)));
        MvcResult resA = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("reportuser_a@example.com", "password123")))).andReturn();
        userSessionA = (MockHttpSession) resA.getRequest().getSession(false);

        RegisterRequest regB = new RegisterRequest("reportuser_b@example.com", "password123", "User B", "+1000000002");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regB)));
        MvcResult resB = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("reportuser_b@example.com", "password123")))).andReturn();
        userSessionB = (MockHttpSession) resB.getRequest().getSession(false);
    }

    @Test
    @DisplayName("Generates monthly report correctly matching PDF contract")
    void testMonthlyReport() throws Exception {
        // Create custom category Freelance for User A
        CategoryRequest catReq = new CategoryRequest("Freelance", "INCOME");
        mockMvc.perform(post("/api/categories").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(catReq)))
                .andExpect(status().isCreated());

        // Transactions in Jan 2024
        // Income: Salary 3000, Freelance 500
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(BigDecimal.valueOf(3000.00), LocalDate.of(2024, 1, 5), "Salary", "Jan Salary"))));
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(BigDecimal.valueOf(500.00), LocalDate.of(2024, 1, 10), "Freelance", "Side gig"))));

        // Expenses: Food 400, Rent 1200, Transportation 200
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(BigDecimal.valueOf(400.00), LocalDate.of(2024, 1, 12), "Food", "Groceries"))));
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(BigDecimal.valueOf(1200.00), LocalDate.of(2024, 1, 15), "Rent", "Apartment"))));
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(BigDecimal.valueOf(200.00), LocalDate.of(2024, 1, 20), "Transportation", "Metro"))));

        // User B transaction in Jan 2024 (should NOT leak into User A's report)
        mockMvc.perform(post("/api/transactions").session(userSessionB).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(BigDecimal.valueOf(9999.00), LocalDate.of(2024, 1, 10), "Salary", "User B salary"))));

        mockMvc.perform(get("/api/reports/monthly/2024/1").session(userSessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month", is(1)))
                .andExpect(jsonPath("$.year", is(2024)))
                .andExpect(jsonPath("$.totalIncome.Salary", is(3000.0)))
                .andExpect(jsonPath("$.totalIncome.Freelance", is(500.0)))
                .andExpect(jsonPath("$.totalExpenses.Food", is(400.0)))
                .andExpect(jsonPath("$.totalExpenses.Rent", is(1200.0)))
                .andExpect(jsonPath("$.totalExpenses.Transportation", is(200.0)))
                .andExpect(jsonPath("$.netSavings", is(1700.0)));
    }

    @Test
    @DisplayName("Generates yearly report correctly matching PDF contract")
    void testYearlyReport() throws Exception {
        // User A transactions across 2024
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(BigDecimal.valueOf(5000.00), LocalDate.of(2024, 3, 1), "Salary", "Salary"))));
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(BigDecimal.valueOf(1500.00), LocalDate.of(2024, 4, 1), "Rent", "Rent"))));

        mockMvc.perform(get("/api/reports/yearly/2024").session(userSessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2024)))
                .andExpect(jsonPath("$.totalIncome.Salary", is(5000.0)))
                .andExpect(jsonPath("$.totalExpenses.Rent", is(1500.0)))
                .andExpect(jsonPath("$.netSavings", is(3500.0)));
    }

    @Test
    @DisplayName("Rejects invalid month with 400 Bad Request")
    void testRejectInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/reports/monthly/2024/13").session(userSessionA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        mockMvc.perform(get("/api/reports/monthly/2024/0").session(userSessionA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Returns empty report with zero net savings for empty period")
    void testEmptyPeriodReport() throws Exception {
        mockMvc.perform(get("/api/reports/monthly/2023/5").session(userSessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month", is(5)))
                .andExpect(jsonPath("$.year", is(2023)))
                .andExpect(jsonPath("$.totalIncome", anEmptyMap()))
                .andExpect(jsonPath("$.totalExpenses", anEmptyMap()))
                .andExpect(jsonPath("$.netSavings", is(0.0)));
    }
}
