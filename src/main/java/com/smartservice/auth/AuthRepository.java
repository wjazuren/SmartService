package com.smartservice.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return jdbc.query("select id, username, password_hash, display_name, role from app_user where username = ?",
                (rs, row) -> new UserAccount(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("display_name"), rs.getString("role")),
                username).stream().findFirst();
    }

    public record UserAccount(long id, String username, String passwordHash, String displayName, String role) {}
}
