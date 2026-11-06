package com.companya.customs.controller;

import com.companya.customs.dto.AuthDtos.*;
import com.companya.customs.entity.User;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.UserRepository;
import com.companya.customs.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserRepository users; private final PasswordEncoder encoder; private final JwtService jwt;
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email())
                .filter(u -> encoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiExceptions.Validation("Invalid email or password"));
        return new LoginResponse(jwt.generate(user.getEmail(), user.getRole().name()), user.getEmail(), user.getRole().name(), user.getFullName());
    }
}
