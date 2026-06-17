import java.io.*;
import java.nio.file.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;

/**
 * Lari Traders Offline Backup Decrypter
 * Decrypts .sql.enc files back to standard .sql database dumps.
 * Usage: java decrypt-backup.java [encrypted-filename]
 */
public class DecryptBackup {
    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("       LARI TRADERS OFFLINE BACKUP DECRYPTER     ");
        System.out.println("=================================================");

        String fileName = null;
        if (args.length >= 1) {
            fileName = args[0];
        } else {
            System.out.print("Enter encrypted backup file name (e.g. fmcg_shop_backup_latest.sql.enc): ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            fileName = reader.readLine();
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            System.out.println("Error: Filename cannot be empty.");
            System.exit(1);
        }
        fileName = fileName.trim();

        Path currentDir = Paths.get(".").toAbsolutePath().normalize();
        Path rootDir = currentDir;
        if (currentDir.getFileName().toString().equalsIgnoreCase("backup") &&
            currentDir.getParent().getFileName().toString().equalsIgnoreCase("scripts")) {
            rootDir = currentDir.getParent().getParent();
        } else if (currentDir.getFileName().toString().equalsIgnoreCase("scripts")) {
            rootDir = currentDir.getParent();
        }
        Path backupDir = rootDir.resolve("backups");
        Path encFile = backupDir.resolve(fileName);

        if (!Files.exists(encFile)) {
            // Check if absolute or relative direct path was provided
            encFile = Paths.get(fileName).toAbsolutePath().normalize();
            if (!Files.exists(encFile)) {
                System.out.println("Error: File not found: " + fileName);
                System.out.println("Please place the file inside " + backupDir.toAbsolutePath() + " or provide the full file path.");
                System.exit(1);
            }
        }

        // Read password from .env
        String password = null;
        Path envPath = rootDir.resolve(".env");
        if (Files.exists(envPath)) {
            for (String line : Files.readAllLines(envPath)) {
                line = line.trim();
                if (line.startsWith("BACKUP_ENCRYPTION_PASSWORD=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length > 1) {
                        password = parts[1].trim();
                        break;
                    }
                }
            }
        }

        if (password == null || password.isEmpty()) {
            System.out.print("Enter Backup Encryption Password (leave empty to try default): ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            password = reader.readLine();
            if (password == null || password.trim().isEmpty()) {
                password = "LariTradersBackupSecure@2026"; // default password fallback
            }
        }

        System.out.println("Decrypting file: " + encFile.toAbsolutePath());
        
        // Derive key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(password.getBytes("UTF-8"));

        try (DataInputStream dis = new DataInputStream(Files.newInputStream(encFile))) {
            int ivLen = dis.readInt();
            byte[] iv = new byte[ivLen];
            dis.readFully(iv);

            int cipherTextLen = dis.readInt();
            byte[] cipherTextBytes = new byte[cipherTextLen];
            dis.readFully(cipherTextBytes);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), spec);

            byte[] plainTextBytes = cipher.doFinal(cipherTextBytes);

            String decName = encFile.getFileName().toString();
            if (decName.endsWith(".enc")) {
                decName = decName.substring(0, decName.length() - 4);
            } else {
                decName = decName + ".dec.sql";
            }
            Path decFile = encFile.resolveSibling(decName);
            Files.write(decFile, plainTextBytes);

            System.out.println("\nSUCCESS!");
            System.out.println("Decrypted file saved to: " + decFile.toAbsolutePath());
            System.out.println("=================================================");
        } catch (Exception e) {
            System.out.println("\nERROR: Decryption failed. Please verify that the password is correct.");
            System.out.println("=================================================");
            System.exit(1);
        }
    }
}
