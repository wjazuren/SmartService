package com.smartservice.security;

public record UserPrincipal(long id, String username, String displayName, String role) {
}
