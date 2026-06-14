package com.shop.modules.backup;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
@Slf4j
public class BackupService {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.backup.drive-folder-id:}")
    private String driveFolderId;

    @Value("${app.backup.key-path:google-drive-key.json}")
    private String keyPath;

    @Value("${app.backup.enabled:true}")
    private boolean backupEnabled;

    @Value("${app.backup.apps-script-url:}")
    private String appsScriptUrl;

    @Value("${app.backup.encryption-password:}")
    private String encryptionPassword;

    private static final String APPLICATION_NAME = "Lari Traders Backup";
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("dd_MM_yyyy");
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    /**
     * Manually triggered backup — returns status map for the controller.
     */
    public Map<String, String> runManualBackup() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            // Step 1: pg_dump
            Path dumpFile = dumpDatabase();

            Path finalFile = dumpFile;
            boolean encrypted = false;
            if (encryptionPassword != null && !encryptionPassword.isBlank()) {
                Path encFile = dumpFile.resolveSibling(dumpFile.getFileName().toString() + ".enc");
                encryptFile(dumpFile, encFile, encryptionPassword);
                Files.deleteIfExists(dumpFile);
                finalFile = encFile;
                encrypted = true;
            }

            result.put("localFile", finalFile.toString());
            result.put("localSizeMB", String.format("%.2f", Files.size(finalFile) / (1024.0 * 1024.0)));
            result.put("encrypted", String.valueOf(encrypted));

            // Step 2: Upload to Google Drive (directly or via Apps Script Web App)
            String driveFileId;
            if (appsScriptUrl != null && !appsScriptUrl.isBlank()) {
                driveFileId = uploadToAppsScript(finalFile);
            } else {
                driveFileId = uploadToDrive(finalFile);
            }
            result.put("driveFileId", driveFileId);
            result.put("status", "SUCCESS");
            result.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FMT));

            log.info("Manual backup completed successfully: {}", finalFile.getFileName());
        } catch (Exception e) {
            log.error("Manual backup failed", e);
            result.put("status", "FAILED");
            result.put("error", cleanErrorMessage(e));
        }
        return result;
    }

    /**
     * Scheduled automatic backup — runs every day at 23:59
     */
    @Scheduled(cron = "0 59 23 * * *")
    public void scheduledBackup() {
        if (!backupEnabled) {
            log.info("Scheduled backup is disabled, skipping.");
            return;
        }
        log.info("Starting scheduled database backup...");
        try {
            Path dumpFile = dumpDatabase();
            Path finalFile = dumpFile;
            if (encryptionPassword != null && !encryptionPassword.isBlank()) {
                Path encFile = dumpFile.resolveSibling(dumpFile.getFileName().toString() + ".enc");
                encryptFile(dumpFile, encFile, encryptionPassword);
                Files.deleteIfExists(dumpFile);
                finalFile = encFile;
            }
            String driveFileId;
            if (appsScriptUrl != null && !appsScriptUrl.isBlank()) {
                driveFileId = uploadToAppsScript(finalFile);
            } else {
                driveFileId = uploadToDrive(finalFile);
            }
            log.info("Scheduled backup completed. Drive file ID: {}", driveFileId);
        } catch (Exception e) {
            log.error("Scheduled backup failed!", e);
        }
    }

    // ==================== Internal Methods ====================

    /**
     * Runs pg_dump and returns the path to the generated .sql file.
     */
    private Path dumpDatabase() throws IOException, InterruptedException {
        String dbName = extractDbName(dbUrl);
        String host = extractHost(dbUrl);
        String port = extractPort(dbUrl);
        String fileName = dbName + "_backup_" + LocalDate.now().format(FILE_DATE_FMT) + ".sql";

        // Store backups in a dedicated folder next to the JAR
        Path backupDir = Paths.get(System.getProperty("user.dir"), "backups");
        Files.createDirectories(backupDir);
        Path outputFile = backupDir.resolve(fileName);

        // Find pg_dump executable
        String pgDump = findPgDump();

        ProcessBuilder pb = new ProcessBuilder(
                pgDump,
                "-h", host,
                "-p", port,
                "-U", dbUsername,
                "-F", "p",         // plain SQL format
                "-f", outputFile.toString(),
                dbName
        );
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(true);

        log.info("Running pg_dump for database '{}' -> {}", dbName, outputFile);
        Process process = pb.start();

        // Read output for logging
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("pg_dump: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("pg_dump exited with code " + exitCode);
        }

        log.info("Database dump completed: {} ({} bytes)", outputFile.getFileName(), Files.size(outputFile));
        return outputFile;
    }

    /**
     * Uploads the given file to Google Drive using a Service Account.
     */
    private String uploadToDrive(Path filePath) throws Exception {
        Path keyFile = Paths.get(keyPath);
        if (!keyFile.isAbsolute()) {
            keyFile = Paths.get(System.getProperty("user.dir")).resolve(keyPath);
        }

        if (!Files.exists(keyFile)) {
            throw new FileNotFoundException(
                    "Google Drive Service Account key file not found at: " + keyFile.toAbsolutePath()
                    + ". Please download the JSON key from Google Cloud Console and place it at this path.");
        }

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(Files.newInputStream(keyFile))
                .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE));

        Drive driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(filePath.getFileName().toString());

        if (driveFolderId != null && !driveFolderId.isBlank()) {
            fileMetadata.setParents(Collections.singletonList(driveFolderId));
        }

        String contentType = filePath.getFileName().toString().endsWith(".enc") ? "application/octet-stream" : "application/sql";
        FileContent mediaContent = new FileContent(contentType, filePath.toFile());

        com.google.api.services.drive.model.File uploadedFile = driveService.files()
                .create(fileMetadata, mediaContent)
                .setSupportsAllDrives(true)
                .setFields("id, name, size")
                .execute();

        log.info("Uploaded to Google Drive: name={}, id={}, size={}", uploadedFile.getName(), uploadedFile.getId(), uploadedFile.getSize());
        return uploadedFile.getId();
    }

    // ==================== Encryption / Decryption Helpers ====================

    private void encryptFile(Path source, Path target, String password) throws Exception {
        byte[] key = deriveKey(password);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, iv);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), spec);
        byte[] plainTextBytes = Files.readAllBytes(source);
        byte[] cipherTextBytes = cipher.doFinal(plainTextBytes);
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(target))) {
            dos.writeInt(iv.length);
            dos.write(iv);
            dos.writeInt(cipherTextBytes.length);
            dos.write(cipherTextBytes);
        }
    }

    private void decryptFile(Path source, Path target, String password) throws Exception {
        byte[] key = deriveKey(password);
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(source))) {
            int ivLen = dis.readInt();
            byte[] iv = new byte[ivLen];
            dis.readFully(iv);
            int cipherTextLen = dis.readInt();
            byte[] cipherTextBytes = new byte[cipherTextLen];
            dis.readFully(cipherTextBytes);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), spec);
            byte[] plainTextBytes = cipher.doFinal(cipherTextBytes);
            Files.write(target, plainTextBytes);
        }
    }

    private byte[] deriveKey(String password) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String decryptBackupFile(String fileName) throws Exception {
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid backup filename");
        }
        if (encryptionPassword == null || encryptionPassword.isBlank()) {
            throw new IllegalStateException("Backup encryption password is not configured.");
        }
        Path backupDir = Paths.get(System.getProperty("user.dir"), "backups");
        Path encFile = backupDir.resolve(fileName);
        if (!Files.exists(encFile)) {
            throw new FileNotFoundException("Encrypted backup file not found: " + fileName);
        }
        if (!fileName.endsWith(".enc")) {
            throw new IllegalArgumentException("File must have .enc extension to be decrypted.");
        }
        String decFileName = fileName.substring(0, fileName.length() - 4); // Remove .enc
        Path decFile = backupDir.resolve(decFileName);
        decryptFile(encFile, decFile, encryptionPassword);
        log.info("Successfully decrypted backup file: {} -> {}", fileName, decFileName);
        return decFile.toString();
    }

    // ==================== Utility Methods ====================

    private String findPgDump() {
        // Try common Windows PostgreSQL installation paths
        String[] candidates = {
                "C:\\Program Files\\PostgreSQL\\16\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\15\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\14\\bin\\pg_dump.exe",
                "pg_dump" // fallback to PATH
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) return path;
        }
        return "pg_dump"; // hope it's on PATH
    }

    private String extractDbName(String jdbcUrl) {
        // jdbc:postgresql://localhost:5432/fmcg_shop_prod -> fmcg_shop_prod
        return jdbcUrl.substring(jdbcUrl.lastIndexOf('/') + 1).split("\\?")[0];
    }

    private String extractHost(String jdbcUrl) {
        // jdbc:postgresql://localhost:5432/db -> localhost
        String afterProtocol = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
        return afterProtocol.split(":")[0];
    }

    private String extractPort(String jdbcUrl) {
        // jdbc:postgresql://localhost:5432/db -> 5432
        String afterProtocol = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
        String hostPort = afterProtocol.split("/")[0];
        return hostPort.contains(":") ? hostPort.split(":")[1] : "5432";
    }

    private String uploadToAppsScript(Path filePath) throws Exception {
        byte[] fileBytes = Files.readAllBytes(filePath);
        String base64Bytes = java.util.Base64.getEncoder().encodeToString(fileBytes);

        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("fileName", filePath.getFileName().toString());
        payload.put("fileBytes", base64Bytes);

        String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                .build();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(appsScriptUrl))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map body = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response.body(), Map.class);
            if ("SUCCESS".equals(body.get("status"))) {
                return (String) body.get("fileId");
            } else {
                throw new IOException("Apps Script error: " + body.get("error"));
            }
        } else {
            throw new java.io.IOException("Failed to upload to Apps Script. Status code: " + response.statusCode());
        }
    }

    private String cleanErrorMessage(Throwable t) {
        if (t == null) {
            return "Unknown error";
        }
        String msg = t.getMessage();
        if (msg == null) {
            msg = t.toString();
        }

        if (msg.contains("storageQuotaExceeded") || msg.contains("Service Accounts do not have storage quota")) {
            return "Google Drive storage quota exceeded. Google Service Accounts have 0 bytes storage by default. Please configure a Shared Drive folder or set up app.backup.apps-script-url in application.properties.";
        }
        if (msg.contains("403 Forbidden") || msg.contains("insufficientPermissions")) {
            return "Google Drive access denied (403). Please share the target backup folder with the Service Account email address in your google-drive-key.json.";
        }
        if (msg.contains("404 Not Found")) {
            return "Google Drive folder not found (404). Please verify that the folder ID in application.properties is correct.";
        }
        if (msg.contains("invalid_grant") || msg.contains("credentials") || msg.contains("FileNotFoundException")) {
            return "Invalid or missing Google Drive API credentials. Please ensure google-drive-key.json is present and valid.";
        }

        if (msg.contains("https://www.googleapis.com/upload/drive")) {
            int jsonStart = msg.indexOf('{');
            if (jsonStart != -1) {
                String jsonPart = msg.substring(jsonStart);
                try {
                    int msgIndex = jsonPart.indexOf("\"message\":");
                    if (msgIndex != -1) {
                        int quoteStart = jsonPart.indexOf("\"", msgIndex + 10);
                        if (quoteStart != -1) {
                            int quoteEnd = jsonPart.indexOf("\"", quoteStart + 1);
                            if (quoteEnd != -1) {
                                return "Google Drive API Error: " + jsonPart.substring(quoteStart + 1, quoteEnd);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            int queryStart = msg.indexOf('?');
            if (queryStart != -1) {
                return "Google Drive Upload Error: " + msg.substring(0, queryStart) + " (Error details in logs)";
            }
        }

        return msg;
    }
}
