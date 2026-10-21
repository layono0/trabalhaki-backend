package com.trabalhaki.backend.service;

import com.trabalhaki.backend.exception.BusinessRuleException;
import com.trabalhaki.backend.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    public static final long MAX_FILE_SIZE = 3 * 1024 * 1024; // 3MB

    @Value("${app.upload.dir:uploads/photos}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
            log.info("Diretório de uploads inicializado em: {}", this.rootLocation);
        } catch (IOException e) {
            log.error("Não foi possível criar o diretório de uploads: {}", e.getMessage());
            throw new BusinessRuleException("Falha ao inicializar o diretório de armazenamento de fotos");
        }
    }

    public String storeProfilePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("O arquivo de foto não pode ser vazio");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessRuleException("O arquivo excede o tamanho máximo permitido de 3MB");
        }

        String extension = validateImageAndGetExtension(file);
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        try {
            Path destinationFile = this.rootLocation.resolve(uniqueFilename).normalize();

            // Prevent path traversal
            if (!destinationFile.startsWith(this.rootLocation)) {
                throw new BusinessRuleException("Tentativa de caminho de arquivo inválida");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Foto de perfil salva com sucesso: {}", uniqueFilename);
            return "/api/v1/files/" + uniqueFilename;
        } catch (IOException e) {
            log.error("Erro ao salvar o arquivo: {}", e.getMessage());
            throw new BusinessRuleException("Falha ao salvar a foto de perfil");
        }
    }

    public Resource loadFileAsResource(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessRuleException("Nome de arquivo inválido");
        }

        try {
            Path filePath = this.rootLocation.resolve(filename).normalize();
            if (!filePath.startsWith(this.rootLocation)) {
                throw new BusinessRuleException("Acesso negado ao arquivo");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Arquivo não encontrado: " + filename);
            }
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException || e instanceof BusinessRuleException) {
                throw (RuntimeException) e;
            }
            throw new ResourceNotFoundException("Arquivo não encontrado: " + filename);
        }
    }

    private String validateImageAndGetExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BusinessRuleException("Tipo de conteúdo do arquivo não especificado");
        }

        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            int bytesRead = is.read(header);
            if (bytesRead < 4) {
                throw new BusinessRuleException("Arquivo de imagem corrompido ou muito curto");
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Não foi possível ler o arquivo enviado");
        }

        // Validate Magic Bytes:
        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return ".jpg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header.length >= 8 &&
                header[0] == (byte) 0x89 && header[1] == (byte) 0x50 &&
                header[2] == (byte) 0x4E && header[3] == (byte) 0x47 &&
                header[4] == (byte) 0x0D && header[5] == (byte) 0x0A &&
                header[6] == (byte) 0x1A && header[7] == (byte) 0x0A) {
            return ".png";
        }

        // WEBP: RIFF....WEBP
        if (header.length >= 12 &&
                header[0] == (byte) 0x52 && header[1] == (byte) 0x49 &&
                header[2] == (byte) 0x46 && header[3] == (byte) 0x46 &&
                header[8] == (byte) 0x57 && header[9] == (byte) 0x45 &&
                header[10] == (byte) 0x42 && header[11] == (byte) 0x50) {
            return ".webp";
        }

        throw new BusinessRuleException("Formato de imagem inválido. Formatos aceitos: JPEG, PNG e WEBP");
    }
}
