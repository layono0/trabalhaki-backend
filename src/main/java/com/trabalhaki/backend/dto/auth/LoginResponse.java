package com.trabalhaki.backend.dto.auth;

import com.trabalhaki.backend.domain.enums.CompanyRole;
import com.trabalhaki.backend.domain.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long expiresInMs;
    private UserSummaryDto user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummaryDto {
        private Long id;
        private String email;
        private Role role;
        private CompanyRole companyRole;
        private Long companyId;
    }
}
