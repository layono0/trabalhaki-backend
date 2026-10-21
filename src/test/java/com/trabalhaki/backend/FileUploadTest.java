package com.trabalhaki.backend;

import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.CandidateProfile;
import com.trabalhaki.backend.domain.model.User;
import com.trabalhaki.backend.repository.CandidateProfileRepository;
import com.trabalhaki.backend.repository.UserRepository;
import com.trabalhaki.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FileUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String candidateJwt;
    private User candidateUser;

    @BeforeEach
    void setupCandidate() {
        String email = "upload.candidato@trabalhaki.com";
        candidateUser = userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(User.builder()
                        .email(email)
                        .password("encoded_pass")
                        .role(Role.CANDIDATE)
                        .active(true)
                        .build())
        );

        candidateProfileRepository.findByUser(candidateUser).orElseGet(() ->
                candidateProfileRepository.save(CandidateProfile.builder()
                        .user(candidateUser)
                        .name("Candidato Teste Upload")
                        .completionPercentage(15)
                        .build())
        );

        candidateJwt = jwtTokenProvider.generateAccessToken(candidateUser);
    }

    @Test
    void shouldUploadValidJpegPhotoSuccessfullyAndServeIt() throws Exception {
        // Valid JPEG header: FF D8 FF E0 + some payload bytes
        byte[] validJpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                validJpegBytes
        );

        var result = mockMvc.perform(multipart("/api/v1/candidates/profile/me/photo")
                        .file(file)
                        .header("Authorization", "Bearer " + candidateJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").isNotEmpty())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("/api/v1/files/"));

        // Extract photoUrl
        CandidateProfile updatedProfile = candidateProfileRepository.findByUser(candidateUser).orElseThrow();
        assertNotNull(updatedProfile.getPhotoUrl());

        String filename = updatedProfile.getPhotoUrl().substring(updatedProfile.getPhotoUrl().lastIndexOf('/') + 1);

        // Fetch the uploaded file via public GET endpoint
        mockMvc.perform(get("/api/v1/files/" + filename))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectFakeImageFileWithInvalidMagicBytes() throws Exception {
        // .png filename and image/png header, but body is malicious text/script
        byte[] fakeBytes = "<?php echo 'malicious'; ?>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                fakeBytes
        );

        mockMvc.perform(multipart("/api/v1/candidates/profile/me/photo")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + candidateJwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/candidates/profile/me/photo")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + candidateJwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectFileExceedingMaxSize() throws Exception {
        // Create > 3MB byte array (3.1 MB)
        byte[] largeBytes = new byte[3 * 1024 * 1024 + 100];
        largeBytes[0] = (byte) 0xFF;
        largeBytes[1] = (byte) 0xD8;
        largeBytes[2] = (byte) 0xFF;

        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "huge.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                largeBytes
        );

        mockMvc.perform(multipart("/api/v1/candidates/profile/me/photo")
                        .file(largeFile)
                        .header("Authorization", "Bearer " + candidateJwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldBlockPathTraversalInFileServing() throws Exception {
        mockMvc.perform(get("/api/v1/files/..%2F..%2Fsecret.txt"))
                .andExpect(status().isBadRequest());
    }
}
