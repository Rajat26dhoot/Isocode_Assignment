package com.companya.customs.config;

import com.companya.customs.entity.Enums;
import com.companya.customs.entity.User;
import com.companya.customs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration @RequiredArgsConstructor
public class AccountBootstrap {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${BOOTSTRAP_EXECUTIVE_EMAIL:}") private String executiveEmail;
    @Value("${BOOTSTRAP_EXECUTIVE_PASSWORD:}") private String executivePassword;
    @Value("${BOOTSTRAP_EXECUTIVE_NAME:Documentation Executive}") private String executiveName;
    @Value("${BOOTSTRAP_MANAGER_EMAIL:}") private String managerEmail;
    @Value("${BOOTSTRAP_MANAGER_PASSWORD:}") private String managerPassword;
    @Value("${BOOTSTRAP_MANAGER_NAME:Documentation Manager}") private String managerName;

    @Bean CommandLineRunner bootstrapAccounts() { return args -> {
        bootstrap(executiveEmail, executivePassword, executiveName, Enums.Role.ROLE_EXECUTIVE);
        bootstrap(managerEmail, managerPassword, managerName, Enums.Role.ROLE_MANAGER);
    }; }

    private void bootstrap(String email, String password, String name, Enums.Role role) {
        if (email == null || email.isBlank() || password == null || password.length() < 12) return;
        if (users.findByEmailIgnoreCase(email).isPresent()) return;
        users.save(User.builder().email(email.trim().toLowerCase()).fullName(name.trim()).passwordHash(encoder.encode(password)).role(role).build());
    }
}
