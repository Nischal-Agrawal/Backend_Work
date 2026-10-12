package com.syfe.personalfinancemanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syfe.personalfinancemanager.dto.request.LoginRequest;
import com.syfe.personalfinancemanager.dto.request.RegisterRequest;
import com.syfe.personalfinancemanager.dto.request.TransactionRequest;
import com.syfe.personalfinancemanager.dto.request.UpdateTransactionRequest;
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
class TransactionIntegrationTest {

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

        RegisterRequest regA = new RegisterRequest("txuser_a@example.com", "password123", "User A", "+1000000001");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regA)));
        MvcResult resA = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("txuser_a@example.com", "password123")))).andReturn();
        userSessionA = (MockHttpSession) resA.getRequest().getSession(false);

        RegisterRequest regB = new RegisterRequest("txuser_b@example.com", "password123", "User B", "+1000000002");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regB)));
        MvcResult resB = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("txuser_b@example.com", "password123")))).andReturn();
        userSessionB = (MockHttpSession) resB.getRequest().getSession(false);
    }

    @Test
    @DisplayName("Creates transaction and derives type correctly")
    void testCreateTransaction() throws Exception {
        TransactionRequest req = new TransactionRequest(
                BigDecimal.valueOf(50000.00),
                LocalDate.of(2024, 1, 15),
                "Salary",
                "January Salary"
        );

        mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.amount", is(50000.0)))
                .andExpect(jsonPath("$.date", is("2024-01-15")))
                .andExpect(jsonPath("$.category", is("Salary")))
                .andExpect(jsonPath("$.description", is("January Salary")))
                .andExpect(jsonPath("$.type", is("INCOME")));
    }

    @Test
    @DisplayName("Rejects transaction with future date")
    void testRejectFutureDate() throws Exception {
        TransactionRequest req = new TransactionRequest(
                BigDecimal.valueOf(100.00),
                LocalDate.now().plusDays(2),
                "Food",
                "Future groceries"
        );

        mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Rejects transaction with non-positive amount")
    void testRejectNonPositiveAmount() throws Exception {
        TransactionRequest req = new TransactionRequest(
                BigDecimal.valueOf(0.00),
                LocalDate.now(),
                "Food",
                "Free food"
        );

        mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rejects transaction with non-existent category")
    void testRejectNonExistentCategory() throws Exception {
        TransactionRequest req = new TransactionRequest(
                BigDecimal.valueOf(100.00),
                LocalDate.now(),
                "NonExistentCategory",
                "Test"
        );

        mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Lists transactions ordered newest date first and applies filters")
    void testListAndFilterTransactions() throws Exception {
        // Create 3 transactions for User A
        TransactionRequest tx1 = new TransactionRequest(BigDecimal.valueOf(1000.00), LocalDate.of(2024, 1, 10), "Salary", "Bonus");
        TransactionRequest tx2 = new TransactionRequest(BigDecimal.valueOf(200.00), LocalDate.of(2024, 1, 15), "Food", "Dinner");
        TransactionRequest tx3 = new TransactionRequest(BigDecimal.valueOf(500.00), LocalDate.of(2024, 2, 1), "Rent", "Feb Rent");

        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(tx1)));
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(tx2)));
        mockMvc.perform(post("/api/transactions").session(userSessionA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(tx3)));

        // Default get: newest first (2024-02-01 first)
        mockMvc.perform(get("/api/transactions").session(userSessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(3)))
                .andExpect(jsonPath("$.transactions[0].category", is("Rent")))
                .andExpect(jsonPath("$.transactions[1].category", is("Food")))
                .andExpect(jsonPath("$.transactions[2].category", is("Salary")));

        // Filter by date range (January only)
        mockMvc.perform(get("/api/transactions")
                        .session(userSessionA)
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)));

        // Filter by type EXPENSE
        mockMvc.perform(get("/api/transactions")
                        .session(userSessionA)
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)));

        // Filter by category Food
        mockMvc.perform(get("/api/transactions")
                        .session(userSessionA)
                        .param("category", "Food"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(1)))
                .andExpect(jsonPath("$.transactions[0].amount", is(200.0)));

        // Invalid date range startDate > endDate
        mockMvc.perform(get("/api/transactions")
                        .session(userSessionA)
                        .param("startDate", "2024-02-01")
                        .param("endDate", "2024-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Updates transaction amount and description")
    void testUpdateTransaction() throws Exception {
        TransactionRequest createReq = new TransactionRequest(
                BigDecimal.valueOf(50000.00),
                LocalDate.of(2024, 1, 15),
                "Salary",
                "January Salary"
        );
        MvcResult res = mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        UpdateTransactionRequest updateReq = new UpdateTransactionRequest(
                BigDecimal.valueOf(60000.00),
                "Salary",
                "Updated January Salary"
        );

        mockMvc.perform(put("/api/transactions/" + id).session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.intValue())))
                .andExpect(jsonPath("$.amount", is(60000.0)))
                .andExpect(jsonPath("$.description", is("Updated January Salary")));
    }

    @Test
    @DisplayName("Rejects updating transaction date")
    void testRejectUpdateDate() throws Exception {
        TransactionRequest createReq = new TransactionRequest(
                BigDecimal.valueOf(50000.00),
                LocalDate.of(2024, 1, 15),
                "Salary",
                "January Salary"
        );
        MvcResult res = mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andReturn();
        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        UpdateTransactionRequest updateReq = new UpdateTransactionRequest();
        updateReq.setDate(LocalDate.of(2024, 1, 20));

        mockMvc.perform(put("/api/transactions/" + id).session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Deletes transaction successfully")
    void testDeleteTransaction() throws Exception {
        TransactionRequest createReq = new TransactionRequest(
                BigDecimal.valueOf(50.00),
                LocalDate.of(2024, 1, 15),
                "Food",
                "Lunch"
        );
        MvcResult res = mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andReturn();
        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/transactions/" + id).session(userSessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Transaction deleted successfully")));

        mockMvc.perform(delete("/api/transactions/" + id).session(userSessionA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Ensures user isolation: User B cannot access or modify User A's transactions")
    void testCrossUserTransactionIsolation() throws Exception {
        TransactionRequest createReq = new TransactionRequest(
                BigDecimal.valueOf(100.00),
                LocalDate.of(2024, 1, 15),
                "Food",
                "Private User A expense"
        );
        MvcResult res = mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andReturn();
        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // User B cannot see it
        mockMvc.perform(get("/api/transactions").session(userSessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(0)));

        // User B cannot update it
        UpdateTransactionRequest updateReq = new UpdateTransactionRequest(BigDecimal.valueOf(200.00), "Food", "Hacked");
        mockMvc.perform(put("/api/transactions/" + id).session(userSessionB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        // User B cannot delete it
        mockMvc.perform(delete("/api/transactions/" + id).session(userSessionB))
                .andExpect(status().isNotFound());
    }
}
