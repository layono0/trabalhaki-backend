package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    java.util.List<User> findByCompanyId(Long companyId);
    Optional<User> findByIdAndCompanyId(Long id, Long companyId);
}
