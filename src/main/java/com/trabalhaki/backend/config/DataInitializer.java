package com.trabalhaki.backend.config;

import com.trabalhaki.backend.domain.enums.CompanyRole;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Seed default candidate account if not exists
        if (!userRepository.existsByEmail("candidato@trabalhaki.com")) {
            User candidate = User.builder()
                    .email("candidato@trabalhaki.com")
                    .password(passwordEncoder.encode("123456"))
                    .role(Role.CANDIDATE)
                    .active(true)
                    .build();
            userRepository.save(candidate);
            log.info("Mock candidate account seeded: candidato@trabalhaki.com");
        }

        // Seed default company account if not exists
        if (!userRepository.existsByEmail("empresa@trabalhaki.com")) {
            User companyAdmin = User.builder()
                    .email("empresa@trabalhaki.com")
                    .password(passwordEncoder.encode("123456"))
                    .role(Role.COMPANY)
                    .companyRole(CompanyRole.ADMIN)
                    .active(true)
                    .build();
            userRepository.save(companyAdmin);
            log.info("Mock company account seeded: empresa@trabalhaki.com");
        }
    }
}
