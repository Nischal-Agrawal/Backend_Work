package com.syfe.personalfinancemanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syfe.personalfinancemanager.dto.request.GoalRequest;
import com.syfe.personalfinancemanager.dto.request.LoginRequest;
import com.syfe.personalfinancemanager.dto.request.RegisterRequest;
import com.syfe.personalfinancemanager.dto.request.TransactionRequest;
import com.syfe.personalfinancemanager.dto.request.UpdateGoalRequest;
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
class GoalIntegrationTest {

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

        RegisterRequest regA = new RegisterRequest("goaluser_a@example.com", "password123", "User A", "+1000000001");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regA)));
        MvcResult resA = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("goaluser_a@example.com", "password123")))).andReturn();
        userSessionA = (MockHttpSession) resA.getRequest().getSession(false);

        RegisterRequest regB = new RegisterRequest("goaluser_b@example.com", "password123", "User B", "+1000000002");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regB)));
        MvcResult resB = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("goaluser_b@example.com", "password123")))).andReturn();
        userSessionB = (MockHttpSession) resB.getRequest().getSession(false);
    }

    @Test
    @DisplayName("Creates goal and calculates live progress accurately")
    void testCreateGoalAndProgress() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusMonths(6);

        // Add an income transaction on today
        TransactionRequest income = new TransactionRequest(
                BigDecimal.valueOf(2000.00),
                today,
                "Salary",
                "Salary"
        );
        mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(income)))
                .andExpect(status().isCreated());

        // Add an expense transaction on today
        TransactionRequest expense = new TransactionRequest(
                BigDecimal.valueOf(500.00),
                today,
                "Food",
                "Groceries"
        );
        mockMvc.perform(post("/api/transactions").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expense)))
                .andExpect(status().isCreated());

        // Create Goal starting today
        GoalRequest goalReq = new GoalRequest(
                "Emergency Fund",
                BigDecimal.valueOf(5000.00),
                futureDate,
                today
        );

        mockMvc.perform(post("/api/goals").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goalName", is("Emergency Fund")))
                .andExpect(jsonPath("$.targetAmount", is(5000.0)))
                .andExpect(jsonPath("$.currentProgress", is(1500.0)))
                .andExpect(jsonPath("$.progressPercentage", is(30.0)))
                .andExpect(jsonPath("$.remainingAmount", is(3500.0)));
    }

    @Test
    @DisplayName("Rejects goal with non-future target date")
    void testRejectPastTargetDate() throws Exception {
        GoalRequest goalReq = new GoalRequest(
                "Old Goal",
                BigDecimal.valueOf(1000.00),
                LocalDate.now().minusDays(1),
                LocalDate.now()
        );

        mockMvc.perform(post("/api/goals").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Rejects goal with non-positive target amount")
    void testRejectNonPositiveTargetAmount() throws Exception {
        GoalRequest goalReq = new GoalRequest(
                "Zero Goal",
                BigDecimal.valueOf(0.00),
                LocalDate.now().plusMonths(1),
                LocalDate.now()
        );

        mockMvc.perform(post("/api/goals").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Updates goal target amount and target date")
    void testUpdateGoal() throws Exception {
        GoalRequest goalReq = new GoalRequest(
                "Vacation Fund",
                BigDecimal.valueOf(3000.00),
                LocalDate.now().plusMonths(3),
                LocalDate.now()
        );

        MvcResult res = mockMvc.perform(post("/api/goals").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        UpdateGoalRequest updateReq = new UpdateGoalRequest(
                BigDecimal.valueOf(4000.00),
                LocalDate.now().plusMonths(5)
        );

        mockMvc.perform(put("/api/goals/" + id).session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.intValue())))
                .andExpect(jsonPath("$.targetAmount", is(4000.0)));
    }

    @Test
    @DisplayName("Deletes savings goal successfully")
    void testDeleteGoal() throws Exception {
        GoalRequest goalReq = new GoalRequest(
                "Temp Goal",
                BigDecimal.valueOf(1000.00),
                LocalDate.now().plusMonths(2),
                LocalDate.now()
        );

        MvcResult res = mockMvc.perform(post("/api/goals").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalReq)))
                .andReturn();
        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/goals/" + id).session(userSessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Goal deleted successfully")));

        mockMvc.perform(get("/api/goals/" + id).session(userSessionA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Ensures user isolation: User B cannot access or affect User A's goals")
    void testGoalUserIsolation() throws Exception {
        GoalRequest goalReq = new GoalRequest(
                "User A Private Goal",
                BigDecimal.valueOf(5000.00),
                LocalDate.now().plusMonths(6),
                LocalDate.now()
        );

        MvcResult res = mockMvc.perform(post("/api/goals").session(userSessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalReq)))
                .andReturn();
        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // User B cannot see it
        mockMvc.perform(get("/api/goals").session(userSessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals", hasSize(0)));

        // User B cannot view it
        mockMvc.perform(get("/api/goals/" + id).session(userSessionB))
                .andExpect(status().isNotFound());

        // User B cannot delete it
        mockMvc.perform(delete("/api/goals/" + id).session(userSessionB))
                .andExpect(status().isNotFound());
    }
}
