package com.trabalhaki.backend.config;

import com.trabalhaki.backend.BackendApplication;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gerencia o ciclo de vida do microserviço Python de validação de certificados
 * embarcado em {@code trabalhaki-cert-validator}, iniciando-o automaticamente
 * junto com o Spring Boot e encerrando-o de forma limpa ao finalizar o back-end.
 */
@Slf4j
@Component
public class CertValidatorProcessManager implements SmartLifecycle {

    private final boolean autoStart;
    private final int port;
    private final String validatorBaseUrl;
    private final String validatorPythonDir;
    private final String pythonExecutable;
    private final long startupTimeoutMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Process managedProcess;
    private volatile Thread shutdownHook;

    public CertValidatorProcessManager(
            @Value("${cert.validator.auto-start:true}") boolean autoStart,
            @Value("${cert.validator.port:8090}") int port,
            @Value("${cert.validator.url:http://localhost:${cert.validator.port:8090}}") String validatorBaseUrl,
            @Value("${cert.validator.python-dir:trabalhaki-cert-validator}") String validatorPythonDir,
            @Value("${cert.validator.python-executable:python3}") String pythonExecutable,
            @Value("${cert.validator.startup-timeout-ms:8000}") long startupTimeoutMs
    ) {
        this.autoStart = autoStart;
        this.port = port;
        this.validatorBaseUrl = normalizeBaseUrl(validatorBaseUrl, port);
        this.validatorPythonDir = validatorPythonDir;
        this.pythonExecutable = pythonExecutable;
        this.startupTimeoutMs = Math.max(1000L, startupTimeoutMs);
    }

    @Override
    public boolean isAutoStartup() {
        return autoStart;
    }

    @Override
    public int getPhase() {
        // Inicia após o servidor HTTP e bancos estarem prontos e encerra primeiro no shutdown
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }

        if (isHealthy()) {
            log.info("Validador de certificados Python já está ativo em {}; reutilizando instância existente.", validatorBaseUrl);
            running.set(true);
            return;
        }

        Path dir = resolveValidatorDirectory();
        if (!Files.isDirectory(dir.resolve("cert_validator"))) {
            log.warn(
                    "Diretório do validador Python não encontrado em '{}' (cert.validator.python-dir='{}'). "
                            + "O back-end seguirá operando sem iniciar o servidor HTTP embarcado.",
                    dir,
                    validatorPythonDir
            );
            return;
        }

        String pythonCmd = resolvePythonExecutable(dir);
        List<String> command = List.of(pythonCmd, "-u", "-m", "cert_validator.app");

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            pb.environment().put("PORT", String.valueOf(port));
            pb.environment().put("PYTHONUNBUFFERED", "1");

            Process process = pb.start();
            this.managedProcess = process;
            registerShutdownHook();
            startLogForwarder(process);

            if (waitUntilHealthy(process)) {
                running.set(true);
                log.info(
                        "Validador de certificados Python iniciado automaticamente em {} (PID={}, dir={})",
                        validatorBaseUrl,
                        process.pid(),
                        dir
                );
            } else if (!process.isAlive()) {
                log.warn(
                        "Validador de certificados Python encerrou prematuramente (exit={}). "
                                + "Verifique se o Flask está instalado ('pip install -r {}/requirements.txt'). "
                                + "Fallback via CLI permanecerá ativo.",
                        process.exitValue(),
                        dir
                );
                this.managedProcess = null;
            } else {
                running.set(true);
                log.warn(
                        "Validador de certificados Python iniciado (PID={}), mas ainda não respondeu em {}/health dentro de {} ms.",
                        process.pid(),
                        validatorBaseUrl,
                        startupTimeoutMs
                );
            }
        } catch (IOException ex) {
            log.warn(
                    "Não foi possível iniciar o validador de certificados Python ({} em {}): {}. "
                            + "Fallback via CLI permanecerá ativo.",
                    pythonCmd,
                    dir,
                    ex.getMessage()
            );
        }
    }

    @Override
    public synchronized void stop() {
        terminateManagedProcess();
        running.set(false);
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @PreDestroy
    public void onDestroy() {
        stop();
    }

    public String getValidatorBaseUrl() {
        return validatorBaseUrl;
    }

    public boolean isManagedProcessAlive() {
        Process proc = this.managedProcess;
        return proc != null && proc.isAlive();
    }

    public boolean isHealthy() {
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(validatorBaseUrl + "/health");
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(600);
            conn.setReadTimeout(1000);
            int status = conn.getResponseCode();
            return status >= 200 && status < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public Path resolveValidatorDirectory() {
        List<Path> candidates = new ArrayList<>();
        if (validatorPythonDir != null && !validatorPythonDir.isBlank()) {
            candidates.add(Paths.get(validatorPythonDir).toAbsolutePath().normalize());
        }
        candidates.add(Paths.get("trabalhaki-cert-validator").toAbsolutePath().normalize());
        candidates.add(Paths.get("trabalhaki-backend", "trabalhaki-cert-validator").toAbsolutePath().normalize());

        Path codeBase = resolveCodeBaseDirectory();
        if (codeBase != null) {
            Path cursor = codeBase;
            for (int depth = 0; depth < 4 && cursor != null; depth++) {
                candidates.add(cursor.resolve("trabalhaki-cert-validator").toAbsolutePath().normalize());
                cursor = cursor.getParent();
            }
        }

        // Compatibilidade legada caso exista diretório irmão
        candidates.add(Paths.get("../trabalhaki-cert-validator").toAbsolutePath().normalize());

        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve("cert_validator"))) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    public String resolvePythonExecutable(Path validatorDir) {
        if (validatorDir != null) {
            List<Path> venvCandidates = List.of(
                    validatorDir.resolve(".venv/bin/python"),
                    validatorDir.resolve(".venv/Scripts/python.exe"),
                    validatorDir.resolve("venv/bin/python"),
                    validatorDir.resolve("venv/Scripts/python.exe")
            );
            for (Path venvPython : venvCandidates) {
                if (Files.isExecutable(venvPython)) {
                    return venvPython.toAbsolutePath().toString();
                }
            }
        }

        if (pythonExecutable != null && !pythonExecutable.isBlank()) {
            String trimmed = pythonExecutable.trim();
            if (isWindows() && "python3".equalsIgnoreCase(trimmed)) {
                return "python";
            }
            return trimmed;
        }
        return isWindows() ? "python" : "python3";
    }

    private boolean waitUntilHealthy(Process process) {
        long deadline = System.currentTimeMillis() + startupTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                return false;
            }
            if (isHealthy()) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isHealthy();
    }

    private void startLogForwarder(Process process) {
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isBlank()) {
                        log.info("[cert-validator] {}", line);
                    }
                }
            } catch (IOException ignored) {
                // Encerramento normal do stream quando o processo finaliza
            }
        }, "cert-validator-log-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void registerShutdownHook() {
        if (this.shutdownHook != null) {
            return;
        }
        Thread hook = new Thread(this::terminateManagedProcess, "cert-validator-shutdown-hook");
        this.shutdownHook = hook;
        try {
            Runtime.getRuntime().addShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM já está encerrando
        }
    }

    private void terminateManagedProcess() {
        Process process = this.managedProcess;
        this.managedProcess = null;
        if (process == null) {
            return;
        }

        try {
            if (process.isAlive()) {
                log.info("Encerrando validador de certificados Python (PID={})...", process.pid());
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } catch (Exception ex) {
            log.debug("Erro ao encerrar processo do validador Python: {}", ex.getMessage());
        }
    }

    private static Path resolveCodeBaseDirectory() {
        try {
            var source = BackendApplication.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Paths.get(source.getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path.getParent() : path;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalizeBaseUrl(String rawUrl, int port) {
        String candidate = (rawUrl == null || rawUrl.isBlank())
                ? "http://localhost:" + port
                : rawUrl.trim();
        return candidate.endsWith("/") ? candidate.substring(0, candidate.length() - 1) : candidate;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
