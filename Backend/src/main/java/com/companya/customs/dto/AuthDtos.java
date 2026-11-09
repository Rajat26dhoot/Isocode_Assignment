package com.companya.customs.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public final class AuthDtos {
    private AuthDtos() { }
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) { }
    public record LoginResponse(String token, String email, String role, String fullName) { }
}
