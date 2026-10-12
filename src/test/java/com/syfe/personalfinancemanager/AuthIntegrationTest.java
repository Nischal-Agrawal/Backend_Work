package com.syfe.personalfinancemanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syfe.personalfinancemanager.dto.request.LoginRequest;
import com.syfe.personalfinancemanager.dto.request.RegisterRequest;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

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

    @BeforeEach
    void cleanUp() {
        transactionRepository.deleteAll();
        goalRepository.deleteAll();
        categoryRepository.findAll().stream().filter(c -> c.isCustom()).forEach(categoryRepository::delete);
        userRepository.deleteAll();
        categoryService.initDefaultCategories();
    }

    @Test
    @DisplayName("Registration succeeds with valid input and hashes password")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com",
                "securePass123",
                "Alice Wonder",
                "+1234567890"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("User registered successfully")))
                .andExpect(jsonPath("$.userId", notNullValue()));
    }

    @Test
    @DisplayName("Registration rejects duplicate email with 409 Conflict")
    void testRegisterDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com",
                "securePass123",
                "Alice Wonder",
                "+1234567890"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")));
    }

    @Test
    @DisplayName("Registration rejects invalid email format with 400 Bad Request")
    void testRegisterInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "invalid-email",
                "securePass123",
                "Alice Wonder",
                "+1234567890"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Registration rejects short password with 400 Bad Request")
    void testRegisterShortPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com",
                "123",
                "Alice Wonder",
                "+1234567890"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Registration rejects blank full name with 400 Bad Request")
    void testRegisterBlankFullName() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com",
                "securePass123",
                "   ",
                "+1234567890"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Registration rejects invalid phone format with 400 Bad Request")
    void testRegisterInvalidPhone() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com",
                "securePass123",
                "Alice Wonder",
                "abc"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login succeeds with correct credentials and establishes session")
    void testLoginSuccess() throws Exception {
        RegisterRequest reg = new RegisterRequest(
                "bob@example.com",
                "password123",
                "Bob Builder",
                "+1987654321"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("bob@example.com", "password123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Login successful")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);

        // Protected endpoint accessible with session
        mockMvc.perform(get("/api/categories").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Login fails with wrong password with 401 Unauthorized")
    void testLoginWrongPassword() throws Exception {
        RegisterRequest reg = new RegisterRequest(
                "bob_wrong@example.com",
                "password123",
                "Bob Builder",
                "+1987654321"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("bob_wrong@example.com", "wrongPass");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Login fails for non-existent user with 401 Unauthorized")
    void testLoginNonExistentUser() throws Exception {
        LoginRequest login = new LoginRequest("ghost@example.com", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Logout invalidates session and prevents further access")
    void testLogoutInvalidatesSession() throws Exception {
        RegisterRequest reg = new RegisterRequest(
                "charlie@example.com",
                "password123",
                "Charlie Brown",
                "+1122334455"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("charlie@example.com", "password123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        // Perform logout
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Logout successful")));

        // Attempting to access protected resource with expired/invalidated session should fail
        mockMvc.perform(get("/api/categories").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated access to protected routes returns 401 Unauthorized")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        mockMvc.perform(get("/api/reports/yearly/2024"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }
}
