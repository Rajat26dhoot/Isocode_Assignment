package com.companya.customs.dto;
public final class AuthDtos {
    private AuthDtos() { }
    public record LoginRequest(String email, String password) { }
    public record LoginResponse(String token, String email, String role, String fullName) { }
}
