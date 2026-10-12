package com.syfe.personalfinancemanager;

import com.syfe.personalfinancemanager.dto.request.LoginRequest;
import com.syfe.personalfinancemanager.dto.request.RegisterRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.RegisterResponse;
import com.syfe.personalfinancemanager.entity.User;
import com.syfe.personalfinancemanager.exception.ConflictException;
import com.syfe.personalfinancemanager.exception.UnauthorizedException;
import com.syfe.personalfinancemanager.repository.UserRepository;
import com.syfe.personalfinancemanager.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    @Mock
    private HttpSession httpSession;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Unit test: register succeeds when email is unique")
    void testRegisterSuccess() {
        RegisterRequest req = new RegisterRequest("test@example.com", "pass123", "Test User", "+123456789");
        when(userRepository.existsByUsername("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("encodedPass");

        User savedUser = new User("test@example.com", "encodedPass", "Test User", "+123456789");
        savedUser.setId(10L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegisterResponse resp = authService.register(req);
        assertEquals("User registered successfully", resp.getMessage());
        assertEquals(10L, resp.getUserId());
    }

    @Test
    @DisplayName("Unit test: register throws ConflictException when email exists")
    void testRegisterDuplicate() {
        RegisterRequest req = new RegisterRequest("test@example.com", "pass123", "Test User", "+123456789");
        when(userRepository.existsByUsername("test@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(req));
    }

    @Test
    @DisplayName("Unit test: login succeeds with valid password")
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest("test@example.com", "pass123");
        User user = new User("test@example.com", "encodedPass", "Test User", "+123456789");
        when(userRepository.findByUsername("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "encodedPass")).thenReturn(true);
        when(httpRequest.getSession(true)).thenReturn(httpSession);

        ApiResponse resp = authService.login(req, httpRequest, httpResponse);
        assertEquals("Login successful", resp.getMessage());
    }

    @Test
    @DisplayName("Unit test: login throws UnauthorizedException for invalid credentials")
    void testLoginInvalid() {
        LoginRequest req = new LoginRequest("test@example.com", "wrong");
        User user = new User("test@example.com", "encodedPass", "Test User", "+123456789");
        when(userRepository.findByUsername("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encodedPass")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(req, httpRequest, httpResponse));
    }

    @Test
    @DisplayName("Unit test: logout invalidates session")
    void testLogout() {
        when(httpRequest.getSession(false)).thenReturn(httpSession);

        ApiResponse resp = authService.logout(httpRequest);
        assertEquals("Logout successful", resp.getMessage());
        verify(httpSession, times(1)).invalidate();
    }

    @Test
    @DisplayName("Unit test: getCurrentUser throws UnauthorizedException when unauthenticated")
    void testGetCurrentUserUnauthenticated() {
        assertThrows(UnauthorizedException.class, () -> authService.getCurrentUser());
    }
}
