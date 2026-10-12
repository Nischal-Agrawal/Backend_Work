package com.syfe.personalfinancemanager.service;

import com.syfe.personalfinancemanager.dto.request.LoginRequest;
import com.syfe.personalfinancemanager.dto.request.RegisterRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.RegisterResponse;
import com.syfe.personalfinancemanager.entity.User;
import com.syfe.personalfinancemanager.exception.ConflictException;
import com.syfe.personalfinancemanager.exception.UnauthorizedException;
import com.syfe.personalfinancemanager.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * Service managing user authentication, registration, session management, and current user resolution.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user.
     *
     * @param request Registration details.
     * @return Registration response containing the generated user ID.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.getUsername().trim().toLowerCase();
        if (userRepository.existsByUsername(normalizedEmail)) {
            throw new ConflictException("User with username " + normalizedEmail + " already exists");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(normalizedEmail, encodedPassword, request.getFullName().trim(), request.getPhoneNumber().trim());
        User saved = userRepository.save(user);

        return new RegisterResponse("User registered successfully", saved.getId());
    }

    /**
     * Authenticates a user and binds their authentication to the HTTP session.
     *
     * @param request Login credentials.
     * @param httpRequest HTTP servlet request.
     * @param httpResponse HTTP servlet response.
     * @return Success API response.
     */
    public ApiResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String normalizedEmail = request.getUsername().trim().toLowerCase();
        User user = userRepository.findByUsername(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return new ApiResponse("Login successful");
    }

    /**
     * Invalidates the current user's session and logs them out.
     *
     * @param request HTTP servlet request.
     * @return Success API response.
     */
    public ApiResponse logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return new ApiResponse("Logout successful");
    }

    /**
     * Retrieves the currently authenticated user.
     *
     * @return Authenticated User entity.
     */
    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication required to access this resource");
        }

        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
