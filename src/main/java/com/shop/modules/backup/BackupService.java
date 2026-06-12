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
            result.put("localFile", dumpFile.toString());
            result.put("localSizeMB", String.format("%.2f", Files.size(dumpFile) / (1024.0 * 1024.0)));

            // Step 2: Upload to Google Drive
            String driveFileId = uploadToDrive(dumpFile);
            result.put("driveFileId", driveFileId);
            result.put("status", "SUCCESS");
            result.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FMT));

            log.info("Manual backup completed successfully: {}", dumpFile.getFileName());
        } catch (Exception e) {
            log.error("Manual backup failed", e);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
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
            String driveFileId = uploadToDrive(dumpFile);
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

        FileContent mediaContent = new FileContent("application/sql", filePath.toFile());

        com.google.api.services.drive.model.File uploadedFile = driveService.files()
                .create(fileMetadata, mediaContent)
                .setSupportsAllDrives(true)
                .setFields("id, name, size")
                .execute();

        log.info("Uploaded to Google Drive: name={}, id={}, size={}", uploadedFile.getName(), uploadedFile.getId(), uploadedFile.getSize());
        return uploadedFile.getId();
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
}
