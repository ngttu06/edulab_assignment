package vn.edu.khtn8.identity.dto;

public record LoginResponse(long expiresInSeconds, String username, String fullName, String role) { }
