package com.chat.controller;

import com.chat.dto.ApiResponse;
import com.chat.dto.UserDTO;
import com.chat.model.User;
import com.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserRepository userRepository;
    
    @GetMapping("/search/username/{username}")
    public ResponseEntity<ApiResponse> getUserByUsername(@PathVariable String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return ResponseEntity.ok(ApiResponse.success("User found", convertToDTO(user)));
    }
    
    @GetMapping("/online")
    public ResponseEntity<ApiResponse> getOnlineUsers() {
        List<User> onlineUsers = userRepository.findOnlineUsers();
        List<UserDTO> userDTOs = onlineUsers.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Online users retrieved", userDTOs));
    }
    
    @GetMapping("/search")
    public ResponseEntity<ApiResponse> searchUsers(@RequestParam String query) {
        List<User> users = userRepository.searchByUsername(query);
        List<UserDTO> userDTOs = users.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Users found", userDTOs));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return ResponseEntity.ok(ApiResponse.success("User retrieved", convertToDTO(user)));
    }
    
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse> getProfile(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", convertToDTO(user)));
    }
    
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse> updateProfile(
            @RequestBody UserDTO userDTO,
            Authentication authentication) {
        
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setProfilePictureUrl(userDTO.getProfilePictureUrl());
        
        userRepository.save(user);
        
        return ResponseEntity.ok(ApiResponse.success("Profile updated", convertToDTO(user)));
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
}