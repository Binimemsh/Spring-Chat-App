package com.chat.controller;

import com.chat.dto.*;
import com.chat.model.User;
import com.chat.repository.UserRepository;
import com.chat.security.UserDetailsServiceImpl;
import com.chat.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        // Get the User entity to get ID
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Generate tokens with user ID
        String jwt = jwtService.generateToken(userDetails, user.getId());
        String refreshToken = jwtService.generateRefreshToken(userDetails, user.getId());
        
        // Update user's last seen and online status
        user.setOnline(true);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
        
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwt);
        data.put("refreshToken", refreshToken);
        data.put("type", "Bearer");
        data.put("username", userDetails.getUsername());
        data.put("roles", userDetails.getAuthorities());
        data.put("userId", user.getId());
        data.put("user", convertToDTO(user));
        
        // Add WebSocket connection info
        data.put("websocketUrl", "ws://localhost:8080/ws");
        
        log.info("✅ User logged in: {} (ID: {})", user.getUsername(), user.getId());
        
        return ResponseEntity.ok(ApiResponse.success("Login successful", data));
    }

    private UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePictureUrl(),
                user.getOnline(),
                user.getLastSeen(),
                user.getCreatedAt(),
                user.getRoles()
        );
    }
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Username is already taken!"));
        }
        
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Email is already in use!"));
        }
        
        // Create new user
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setOnline(false);
        
        // Set default role
        user.setRoles(new HashSet<>());
        user.getRoles().add("ROLE_USER");
        
        userRepository.save(user);
        
        return ResponseEntity.ok(ApiResponse.success("User registered successfully", null));
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        String refreshToken = refreshTokenRequest.getRefreshToken();
        
        if (!jwtService.isRefreshToken(refreshToken)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid refresh token"));
        }
        
        try {
            String username = jwtService.extractUsername(refreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            if (jwtService.validateToken(refreshToken, userDetails)) {
                Long userId = jwtService.extractUserId(refreshToken);
                String newToken = jwtService.generateToken(userDetails, userId);
                String newRefreshToken = jwtService.generateRefreshToken(userDetails, userId);
                
                Map<String, Object> data = new HashMap<>();
                data.put("token", newToken);
                data.put("refreshToken", newRefreshToken);
                data.put("type", "Bearer");
                
                return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", data));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid refresh token"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid refresh token: " + e.getMessage()));
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logoutUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username).orElse(null);
            
            if (user != null) {
                user.setOnline(false);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
            }
            
            SecurityContextHolder.clearContext();
        }
        
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
    
    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated() || 
                authentication.getName().equals("anonymousUser")) {
                log.error("Authentication is null or anonymous");
                return ResponseEntity.status(401)
                        .body(ApiResponse.error("Not authenticated"));
            }
            
            String username = authentication.getName();
            log.info("=== /api/auth/me ===");
            log.info("Username from authentication: '{}'", username);
            
            // Try to find the user
            Optional<User> userOptional = userRepository.findByUsername(username);
            
            if (userOptional.isEmpty()) {
                log.error("User '{}' not found!", username);
                return ResponseEntity.status(404)
                        .body(ApiResponse.error("User '" + username + "' not found. Please login again."));
            }
            
            User user = userOptional.get();
            log.info("User found: {} (ID: {})", user.getUsername(), user.getId());
            
            UserDTO userDTO = convertToDTO(user);
            return ResponseEntity.ok(ApiResponse.success("User retrieved", userDTO));
            
        } catch (Exception e) {
            log.error("Error in /api/auth/me: ", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }
}