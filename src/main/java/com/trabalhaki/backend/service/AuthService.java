package com.trabalhaki.backend.service;

import com.trabalhaki.backend.domain.enums.CompanyRole;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.RefreshToken;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.dto.auth.*;
import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.RefreshTokenRepository;
import com.trabalhaki.backend.repository.UserRepository;
import com.trabalhaki.backend.security.JwtTokenProvider;
import com.trabalhaki.backend.util.CnpjValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("Email ou senha inválidos"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Conta de usuário inativa");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Email ou senha inválidos");
        }

        return createAuthResponse(user);
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido"));

        if (refreshToken.isRevoked() || refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expirado ou revogado");
        }

        User user = refreshToken.getUser();
        if (!user.isActive()) {
            throw new UnauthorizedException("Usuário inativo");
        }

        String newAccessToken = tokenProvider.generateAccessToken(user);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .expiresInMs(tokenProvider.getExpirationMs())
                .user(mapToUserSummary(user))
                .build();
    }

    @Transactional
    public LoginResponse registerCandidate(RegisterCandidateRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email já cadastrado no sistema");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CANDIDATE)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        return createAuthResponse(savedUser);
    }

    @Transactional
    public LoginResponse registerCompany(RegisterCompanyRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email já cadastrado no sistema");
        }

        if (!CnpjValidator.isValid(request.getCnpj())) {
            throw new BusinessRuleException("CNPJ inválido estruturalmente");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.COMPANY)
                .companyRole(CompanyRole.ADMIN)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        return createAuthResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public LoginResponse.UserSummaryDto getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return mapToUserSummary(user);
    }

    private LoginResponse createAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresInMs(tokenProvider.getExpirationMs())
                .user(mapToUserSummary(user))
                .build();
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private LoginResponse.UserSummaryDto mapToUserSummary(User user) {
        return LoginResponse.UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .companyRole(user.getCompanyRole())
                .companyId(user.getCompanyId())
                .build();
    }
}
