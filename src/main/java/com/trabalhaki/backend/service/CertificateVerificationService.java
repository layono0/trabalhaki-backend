package com.trabalhaki.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trabalhaki.backend.config.CertValidatorProcessManager;
import com.trabalhaki.backend.domain.enums.Role;
import com.trabalhaki.backend.domain.model.*;
import com.trabalhaki.backend.dto.candidate.CandidateCertificateResponse;
import com.trabalhaki.backend.dto.candidate.CertificateValidationResponse;
import com.trabalhaki.backend.dto.candidate.ForensicCheckDto;
import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import com.trabalhaki.backend.exception.UnauthorizedException;
import com.trabalhaki.backend.repository.CandidateCertificateRepository;
import com.trabalhaki.backend.repository.CandidateProfileRepository;
import com.trabalhaki.backend.repository.SkillRepository;
import com.trabalhaki.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateVerificationService {

    public static final long MAX_CERTIFICATE_SIZE = 5 * 1024 * 1024; // 5MB

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateCertificateRepository candidateCertificateRepository;
    private final SkillRepository skillRepository;
    private final CertValidatorProcessManager certValidatorProcessManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<CandidateCertificateResponse> listMyCertificates(String email) {
        CandidateProfile profile = getCandidateProfileByEmail(email);
        return candidateCertificateRepository.findByCandidateOrderByCreatedAtDesc(profile)
                .stream()
                .map(this::toCertificateResponse)
                .toList();
    }

    @Transactional
    public void deleteMyCertificate(String email, Long certificateId) {
        CandidateProfile profile = getCandidateProfileByEmail(email);
        CandidateCertificate cert = candidateCertificateRepository.findByIdAndCandidate(certificateId, profile)
                .orElseThrow(() -> new ResourceNotFoundException("Certificado não encontrado"));

        if (profile.getCertificates() != null) {
            profile.getCertificates().removeIf(c -> c.getId().equals(cert.getId()));
        }
        candidateCertificateRepository.delete(cert);
    }

    @Transactional
    public CertificateValidationResponse validateAndStoreCertificate(
            String email,
            MultipartFile file,
            String targetSkill
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Selecione um arquivo de certificado (PDF, PNG ou JPEG).");
        }
        if (file.getSize() > MAX_CERTIFICATE_SIZE) {
            throw new BusinessRuleException("O certificado excede o limite máximo permitido de 5 MB.");
        }

        CandidateProfile profile = getCandidateProfileByEmail(email);
        String candidateName = profile.getName() != null ? profile.getName().trim() : "";
        String[] nameTokens = candidateName.split("\\s+");
        if (candidateName.isBlank() || candidateName.equalsIgnoreCase("Candidato") || nameTokens.length < 2) {
            throw new BusinessRuleException(
                    "Atualize seu nome completo (nome e sobrenome) no perfil antes de validar um certificado."
            );
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("Não foi possível ler o arquivo enviado.");
        }

        String sha256 = sha256Hex(fileBytes);
        if (candidateCertificateRepository.existsByFileSha256(sha256)) {
            return rejectedResponse(
                    "Este arquivo de certificado já foi validado anteriormente no sistema.",
                    List.of(new ForensicCheckDto(
                            "DUPLICATE_FILE_HASH",
                            "HIGH",
                            false,
                            70,
                            "Hash SHA-256 idêntico já registrado na plataforma."
                    ))
            );
        }

        JsonNode pythonResult = invokePythonValidator(
                fileBytes,
                file.getOriginalFilename(),
                candidateName,
                targetSkill
        );

        boolean accepted = pythonResult.path("accepted").asBoolean(false);
        String status = pythonResult.path("status").asText(accepted ? "VERIFIED" : "REJECTED");
        String courseTitle = textOrNull(pythonResult.get("courseTitle"));
        JsonNode instNode = pythonResult.path("institution");
        String institutionName = textOrNull(instNode.get("name"));
        Integer institutionTier = instNode.path("tier").isNumber() ? instNode.path("tier").asInt() : null;
        Integer workloadHours = pythonResult.path("workloadHours").isNumber()
                ? pythonResult.path("workloadHours").asInt()
                : null;
        int priorityBonus = pythonResult.path("priorityBonus").asInt(0);
        int trustScore = pythonResult.path("trustScore").asInt(0);
        int fraudRiskScore = pythonResult.path("fraudRiskScore").asInt(0);
        String credentialCode = textOrNull(pythonResult.get("credentialCode"));
        String summary = pythonResult.path("summary").asText("");

        List<String> verifiedSkills = readStringList(pythonResult.get("verifiedSkills"));
        List<String> rejectionReasons = new ArrayList<>(readStringList(pythonResult.get("rejectionReasons")));
        List<String> whatWasChecked = readStringList(pythonResult.get("whatWasChecked"));
        List<ForensicCheckDto> forensicChecks = readForensicChecks(pythonResult.get("forensicChecks"));

        // Prevent same credential code from being claimed by a different candidate account
        if (accepted && credentialCode != null
                && (candidateCertificateRepository.existsByCredentialCodeIgnoreCaseAndCandidateIdNot(credentialCode, profile.getId())
                || candidateCertificateRepository.existsByCredentialCodeIgnoreCaseAndCandidateId(credentialCode, profile.getId()))) {
            accepted = false;
            status = "REJECTED";
            priorityBonus = 0;
            fraudRiskScore = Math.max(fraudRiskScore, 75);
            String msg = "O código de autenticidade deste certificado já está vinculado a uma validação anterior.";
            rejectionReasons.add(msg);
            forensicChecks.add(new ForensicCheckDto("CREDENTIAL_REPLAY", "HIGH", false, 75, msg));
            summary = msg;
        }

        Long savedCertId = null;
        if (accepted) {
            CandidateCertificate cert = CandidateCertificate.builder()
                    .candidate(profile)
                    .fileSha256(sha256)
                    .credentialCode(truncate(credentialCode, 180))
                    .courseTitle(truncate(courseTitle != null ? courseTitle : "Certificação Verificada", 200))
                    .institutionName(truncate(institutionName != null ? institutionName : "Instituição Reconhecida", 150))
                    .institutionTier(institutionTier != null ? institutionTier : 3)
                    .verifiedSkillsCsv(truncate(String.join(", ", verifiedSkills), 500))
                    .workloadHours(workloadHours)
                    .priorityBonus(priorityBonus)
                    .trustScore(trustScore)
                    .fraudRiskScore(fraudRiskScore)
                    .verificationSummary(truncate(summary, 500))
                    .build();

            CandidateCertificate saved = candidateCertificateRepository.save(cert);
            savedCertId = saved.getId();
            if (profile.getCertificates() != null) {
                profile.getCertificates().add(saved);
            }

            linkVerifiedSkillsToProfile(profile, verifiedSkills);
            candidateProfileRepository.save(profile);
        }

        return new CertificateValidationResponse(
                accepted,
                status,
                savedCertId,
                courseTitle,
                institutionName,
                institutionTier,
                verifiedSkills,
                workloadHours,
                priorityBonus,
                trustScore,
                fraudRiskScore,
                credentialCode,
                rejectionReasons,
                forensicChecks,
                whatWasChecked,
                summary
        );
    }

    private void linkVerifiedSkillsToProfile(CandidateProfile profile, List<String> verifiedSkills) {
        if (verifiedSkills.isEmpty()) {
            return;
        }
        if (profile.getSkills() == null) {
            profile.setSkills(new ArrayList<>());
        }
        for (String skillName : verifiedSkills) {
            boolean alreadyHas = profile.getSkills().stream()
                    .anyMatch(cs -> cs.getSkill() != null
                            && cs.getSkill().getName().equalsIgnoreCase(skillName));
            if (!alreadyHas) {
                Skill skill = skillRepository.findByNameIgnoreCase(skillName)
                        .orElseGet(() -> skillRepository.save(Skill.builder().name(skillName).build()));
                profile.getSkills().add(CandidateSkill.builder()
                        .candidate(profile)
                        .skill(skill)
                        .build());
            }
        }
    }

    private JsonNode invokePythonValidator(
            byte[] fileBytes,
            String originalFilename,
            String candidateName,
            String targetSkill
    ) {
        String safeName = (originalFilename != null && !originalFilename.isBlank())
                ? originalFilename
                : "certificate.pdf";

        String validatorBaseUrl = certValidatorProcessManager.getValidatorBaseUrl();

        // 1. Try Python HTTP microservice first if running
        try {
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(1500);
            rf.setReadTimeout(10000);
            RestClient client = RestClient.builder()
                    .baseUrl(validatorBaseUrl)
                    .requestFactory(rf)
                    .build();

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("candidateName", candidateName);
            if (targetSkill != null && !targetSkill.isBlank()) {
                body.add("targetSkill", targetSkill.trim());
            }
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return safeName;
                }
            });

            String rawJson = client.post()
                    .uri("/api/v1/validate")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (rawJson != null && !rawJson.isBlank()) {
                return objectMapper.readTree(rawJson);
            }
        } catch (Exception httpEx) {
            log.debug("Validador HTTP indisponível ({}), usando CLI Python local: {}", validatorBaseUrl, httpEx.getMessage());
        }

        // 2. Fallback to local Python CLI subprocess
        String lower = safeName.toLowerCase(Locale.ROOT);
        String suffix = lower.endsWith(".png") ? ".png"
                : (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ? ".jpg" : ".pdf";

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("trabalhaki-cert-", suffix);
            Files.write(tempFile, fileBytes);

            Path validatorDir = certValidatorProcessManager.resolveValidatorDirectory();
            List<String> cmd = new ArrayList<>();
            cmd.add(certValidatorProcessManager.resolvePythonExecutable(validatorDir));
            cmd.add("-m");
            cmd.add("cert_validator.cli");
            cmd.add("validate");
            cmd.add("--file");
            cmd.add(tempFile.toAbsolutePath().toString());
            cmd.add("--candidate-name");
            cmd.add(candidateName);
            if (targetSkill != null && !targetSkill.isBlank()) {
                cmd.add("--target-skill");
                cmd.add(targetSkill.trim());
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(validatorDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessRuleException("Tempo limite excedido ao analisar o certificado.");
            }
            if (process.exitValue() != 0 || stdout.isBlank()) {
                log.error("Erro no validador Python (exit={}): {}", process.exitValue(), stderr);
                throw new BusinessRuleException("Falha ao processar a verificação do certificado.");
            }
            return objectMapper.readTree(stdout);
        } catch (BusinessRuleException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("Falha ao executar validador Python: {}", e.getMessage());
            throw new BusinessRuleException("Serviço verificador de certificados indisponível no momento.");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private CandidateProfile getCandidateProfileByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        if (user.getRole() != Role.CANDIDATE) {
            throw new UnauthorizedException("Apenas candidatos podem gerenciar certificados.");
        }
        return candidateProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de candidato não encontrado"));
    }

    private CandidateCertificateResponse toCertificateResponse(CandidateCertificate cert) {
        return new CandidateCertificateResponse(
                cert.getId(),
                cert.getCourseTitle(),
                cert.getInstitutionName(),
                cert.getInstitutionTier(),
                cert.getVerifiedSkillsList(),
                cert.getWorkloadHours(),
                cert.getPriorityBonus(),
                cert.getTrustScore(),
                cert.getCredentialCode(),
                cert.getVerificationSummary(),
                cert.getCreatedAt()
        );
    }

    private CertificateValidationResponse rejectedResponse(String reason, List<ForensicCheckDto> checks) {
        return new CertificateValidationResponse(
                false,
                "REJECTED",
                null,
                null,
                null,
                null,
                List.of(),
                null,
                0,
                0,
                70,
                null,
                List.of(reason),
                checks,
                List.of("Verificação de unicidade criptográfica (SHA-256)"),
                reason
        );
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String val = node.asText("").trim();
        return val.isEmpty() ? null : val;
    }

    private static List<String> readStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String v = item.asText("").trim();
                if (!v.isEmpty()) list.add(v);
            }
        }
        return list;
    }

    private static List<ForensicCheckDto> readForensicChecks(JsonNode node) {
        List<ForensicCheckDto> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(new ForensicCheckDto(
                        item.path("code").asText(""),
                        item.path("severity").asText("INFO"),
                        item.path("passed").asBoolean(false),
                        item.path("points").asInt(0),
                        item.path("detail").asText("")
                ));
            }
        }
        return list;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
