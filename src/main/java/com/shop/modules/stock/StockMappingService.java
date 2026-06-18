package com.shop.modules.stock;

import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockMappingService {

    private final ProductRepository productRepository;
    private final StockBatchRepository batchRepository;

    @Data
    @Builder
    public static class RawInvoiceItem {
        private String name;
        private BigDecimal mrp;
        private String batchNumber;
        private LocalDate expiryDate;
        private int invoiceCases;
        private int packsPerCase;
        private BigDecimal buyPricePerPiece;
        private BigDecimal taxableValue;
        private BigDecimal gstPercent;
        private int offerUnitsDetected; // free units detected from OCR
    }

    @Data
    @Builder
    public static class MappedStockPreview {
        private UUID productId;
        private String productName;
        private String brand;
        private String category;
        private BigDecimal mrp;
        private String batchNumber;
        private LocalDate expiryDate;
        private int invoiceCases;
        private int packsPerCase;
        private BigDecimal buyPricePerPiece;
        private BigDecimal taxableValue;
        private BigDecimal gstPercent;
        private String primaryUnit;
        private String secondaryUnit;
        private int secondaryPerPrimary;
        private int primaryAdded;
        private int secondaryAdded;
        private int openBoxAdded;
        private BigDecimal buyPriceWithoutTax; // Price per Primary BOX
        private boolean duplicateBatch;
        private boolean newProduct;
        private int offerUnitsAdded; // free units detected from OCR (default 0, user can edit)
    }

    public List<MappedStockPreview> mapInvoiceItems(List<RawInvoiceItem> rawItems) {
        List<MappedStockPreview> previewList = new ArrayList<>();
        List<Product> allProducts = productRepository.findByActiveTrue();

        for (RawInvoiceItem rawItem : rawItems) {
            Product bestMatch = findBestProductMatch(rawItem.getName(), rawItem.getMrp(), allProducts);
            boolean isNewProduct = (bestMatch == null);

            boolean duplicateBatch = !isNewProduct && checkDuplicateBatch(bestMatch.getId(), rawItem.getBatchNumber());

            MappedStockPreview.MappedStockPreviewBuilder builder = MappedStockPreview.builder()
                    .batchNumber(rawItem.getBatchNumber())
                    .expiryDate(rawItem.getExpiryDate())
                    .invoiceCases(rawItem.getInvoiceCases())
                    .packsPerCase(rawItem.getPacksPerCase())
                    .buyPricePerPiece(rawItem.getBuyPricePerPiece())
                    .taxableValue(rawItem.getTaxableValue())
                    .gstPercent(rawItem.getGstPercent())
                    .mrp(rawItem.getMrp())
                    .duplicateBatch(duplicateBatch);

            if (isNewProduct) {
                // If it is a new product, propose values based on invoice
                BigDecimal buyPriceWithoutTax = null;
                if (rawItem.getTaxableValue() != null && rawItem.getTaxableValue().compareTo(BigDecimal.ZERO) > 0 && rawItem.getInvoiceCases() > 0) {
                    buyPriceWithoutTax = rawItem.getTaxableValue().divide(BigDecimal.valueOf(rawItem.getInvoiceCases()), 2, RoundingMode.HALF_UP);
                } else {
                    buyPriceWithoutTax = rawItem.getBuyPricePerPiece().multiply(BigDecimal.valueOf(rawItem.getPacksPerCase())).setScale(2, RoundingMode.HALF_UP);
                }

                String secUnit = (rawItem.getPacksPerCase() == 72 || rawItem.getPacksPerCase() == 216) ? "PACK" : "LADI";
                int proposedRatio = 20;
                if ("PACK".equals(secUnit)) {
                    proposedRatio = rawItem.getPacksPerCase() > 0 ? rawItem.getPacksPerCase() : 72;
                } else {
                    BigDecimal mrpVal = rawItem.getMrp() != null ? rawItem.getMrp() : BigDecimal.ZERO;
                    int ladiSize = (mrpVal.compareTo(BigDecimal.valueOf(10)) > 0) ? 10 : 12;
                    proposedRatio = rawItem.getPacksPerCase() > 0 ? (rawItem.getPacksPerCase() / ladiSize) : 20;
                    if (proposedRatio <= 0) {
                        proposedRatio = 20;
                    }
                }

                int secondaryAdded = rawItem.getInvoiceCases() * proposedRatio;

                builder.newProduct(true)
                        .productName(rawItem.getName())
                        .brand("Haldiram's") // Default guess
                        .category(rawItem.getName().toLowerCase().contains("chip") ? "CHIPS" : "SNACKS")
                        .primaryUnit("BOX")
                        .secondaryUnit(secUnit)
                        .secondaryPerPrimary(proposedRatio)
                        .primaryAdded(rawItem.getInvoiceCases())
                        .secondaryAdded(secondaryAdded)
                        .openBoxAdded(0)
                        .buyPriceWithoutTax(buyPriceWithoutTax)
                        .offerUnitsAdded(rawItem.getOfferUnitsDetected());
            } else {
                // Map based on existing product's DB configurations
                int ratio = bestMatch.getSecondaryPerPrimary() != null ? bestMatch.getSecondaryPerPrimary() : 1;
                String secUnit = bestMatch.getSecondaryUnit() != null ? bestMatch.getSecondaryUnit().toUpperCase() : "LADI";
                
                int packPerSecondary = 1;
                if ("LADI".equals(secUnit)) {
                    if (ratio > 0) {
                        packPerSecondary = rawItem.getPacksPerCase() / ratio;
                    } else {
                        packPerSecondary = 12;
                    }
                }
                if (packPerSecondary <= 0) {
                    packPerSecondary = 1;
                }

                int totalPacks = rawItem.getInvoiceCases() * rawItem.getPacksPerCase();
                int totalSecondaryUnits = totalPacks / packPerSecondary;

                int primaryAdded = totalSecondaryUnits / ratio;
                int openBoxAdded = totalSecondaryUnits % ratio;

                BigDecimal buyPriceWithoutTax = null;
                if (rawItem.getTaxableValue() != null && rawItem.getTaxableValue().compareTo(BigDecimal.ZERO) > 0 && rawItem.getInvoiceCases() > 0) {
                    buyPriceWithoutTax = rawItem.getTaxableValue().divide(BigDecimal.valueOf(rawItem.getInvoiceCases()), 2, RoundingMode.HALF_UP);
                } else {
                    buyPriceWithoutTax = rawItem.getBuyPricePerPiece()
                            .multiply(BigDecimal.valueOf(ratio * packPerSecondary))
                            .setScale(2, RoundingMode.HALF_UP);
                }

                builder.newProduct(false)
                        .productId(bestMatch.getId())
                        .productName(rawItem.getName()) // Keep raw invoice name
                        .brand(bestMatch.getBrand())
                        .category(bestMatch.getCategory() != null ? bestMatch.getCategory().name() : "SNACKS")
                        .primaryUnit(bestMatch.getPrimaryUnit() != null ? bestMatch.getPrimaryUnit() : "BOX")
                        .secondaryUnit(bestMatch.getSecondaryUnit() != null ? bestMatch.getSecondaryUnit() : "LADI")
                        .secondaryPerPrimary(ratio)
                        .primaryAdded(primaryAdded)
                        .secondaryAdded(totalSecondaryUnits)
                        .openBoxAdded(openBoxAdded)
                        .buyPriceWithoutTax(buyPriceWithoutTax)
                        .offerUnitsAdded(rawItem.getOfferUnitsDetected());
            }

            previewList.add(builder.build());
        }

        return previewList;
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replaceAll("[-/\\\\*|().,;:&+_]", " ")
                .replaceAll("(\\d+)([a-zA-Z]+)", "$1 $2")
                .replaceAll("([a-zA-Z]+)(\\d+)", "$1 $2")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double jaroSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length(), len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchLimit = Math.max(0, Math.max(len1, len2) / 2 - 1);
        boolean[] match1 = new boolean[len1];
        boolean[] match2 = new boolean[len2];

        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchLimit);
            int end = Math.min(len2, i + matchLimit + 1);
            for (int j = start; j < end; j++) {
                if (!match2[j] && s1.charAt(i) == s2.charAt(j)) {
                    match1[i] = true;
                    match2[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) return 0.0;

        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (match1[i]) {
                while (!match2[k]) k++;
                if (s1.charAt(i) != s2.charAt(k)) {
                    transpositions++;
                }
                k++;
            }
        }

        return ((double) matches / len1 + (double) matches / len2 + (double) (matches - transpositions / 2.0) / matches) / 3.0;
    }

    private double jaroWinklerSimilarity(String s1, String s2) {
        double jaro = jaroSimilarity(s1, s2);
        if (jaro < 0.7) return jaro;

        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        return jaro + 0.1 * prefix * (1.0 - jaro);
    }

    private Product findBestProductMatch(String rawName, BigDecimal rawMrp, List<Product> allProducts) {
        if (rawName == null || rawName.trim().isEmpty()) return null;
        String normalizedRaw = normalizeName(rawName);
        if (normalizedRaw.isEmpty()) return null;

        Product bestMatch = null;
        double bestScore = 0.0;

        for (Product product : allProducts) {
            // Check for MRP mismatch
            if (isMrpMismatch(rawMrp, product.getName())) {
                continue;
            }

            // Check for flavor mismatch
            if (isFlavorMismatch(rawName, product.getName())) {
                continue;
            }

            String dbNameLower = product.getName().toLowerCase();
            String normalizedDb = normalizeName(product.getName());

            // Exact match
            if (dbNameLower.equals(rawName.trim().toLowerCase()) || normalizedDb.equals(normalizedRaw)) {
                return product;
            }

            // Word overlap check on normalized names
            String[] rawWords = normalizedRaw.split("\\s+");
            String[] dbWords = normalizedDb.split("\\s+");

            int overlapCount = 0;
            int validRawWordsCount = 0;
            for (String rawWord : rawWords) {
                // Skip common packaging unit tokens
                if (rawWord.equals("mrp") || rawWord.equals("rs") || rawWord.equals("gm") || rawWord.equals("kg") || rawWord.equals("ml") || rawWord.equals("l")) {
                    continue;
                }
                validRawWordsCount++;
                for (String dbWord : dbWords) {
                    if (dbWord.equals(rawWord) || jaroWinklerSimilarity(rawWord, dbWord) > 0.85) {
                        overlapCount++;
                        break;
                    }
                }
            }

            if (validRawWordsCount == 0) continue;

            double score = (double) overlapCount / Math.max(validRawWordsCount, dbWords.length);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = product;
            }
        }

        // Return match if confidence score is reasonably high (score > 0.4)
        if (bestScore > 0.4) {
            return bestMatch;
        }

        return null;
    }

    private boolean isMrpMismatch(BigDecimal rawMrp, String dbProductName) {
        if (rawMrp == null) return false;
        int rawMrpInt = rawMrp.setScale(0, RoundingMode.HALF_UP).intValue();
        if (rawMrpInt <= 0) return false;

        String nameLower = dbProductName.toLowerCase();
        boolean foundPriceIndicator = false;
        String rawMrpStr = String.valueOf(rawMrpInt);

        // 1. Match patterns like "rs-X", "rs X", "mrp X", "mrp-X", "wrp-X", "wrp X"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:rs|mrp|wrp)[-\\s.]*(\\d+)");
        java.util.regex.Matcher m = p.matcher(nameLower);
        while (m.find()) {
            foundPriceIndicator = true;
            String valStr = m.group(1);
            if (valStr.equals(rawMrpStr)) {
                return false; // Exact match only — Rs5 != Rs50, Rs20 != Rs200
            }
        }

        // 2. Match patterns like "/X" or "-X" at the end of the name or before word boundaries (e.g. "/5" or "-20"), excluding weight/volume units
        java.util.regex.Pattern pEnd = java.util.regex.Pattern.compile("[/-]\\s*(\\d+)(?!\\s*(?:gm|kg|ml|l|g)\\b)(?:\\s|$|/)");
        java.util.regex.Matcher mEnd = pEnd.matcher(nameLower);
        while (mEnd.find()) {
            foundPriceIndicator = true;
            String valStr = mEnd.group(1);
            if (valStr.equals(rawMrpStr)) {
                return false; // Exact match only — Rs5 != Rs50, Rs20 != Rs200
            }
        }

        // If database product name has a price indicator but none of them matched our raw invoice MRP, it is a mismatch
        if (foundPriceIndicator) {
            return true;
        }

        return false;
    }

    private boolean isFlavorMismatch(String rawName, String dbName) {
        String[] flavors = {"tomato", "salted", "classic", "masala", "pudina", "onion", "chilli", "thai", "cream"};
        String rawLower = rawName.toLowerCase();
        String dbLower = dbName.toLowerCase();
        
        String rawFlavor = null;
        String dbFlavor = null;
        
        for (String f : flavors) {
            if (rawLower.contains(f)) {
                rawFlavor = f;
            }
            if (dbLower.contains(f)) {
                dbFlavor = f;
            }
        }
        
        if (rawFlavor != null && dbFlavor != null && !rawFlavor.equals(dbFlavor)) {
            // "classic" and "salted" are allowed to overlap (e.g. "classic salted" vs "salted")
            if ((rawFlavor.equals("classic") && dbFlavor.equals("salted")) || 
                (rawFlavor.equals("salted") && dbFlavor.equals("classic"))) {
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean checkDuplicateBatch(UUID productId, String batchNumber) {
        if (productId == null || batchNumber == null || batchNumber.trim().isEmpty()) return false;
        return batchRepository.existsByProductIdAndBatchNumberIgnoreCase(productId, batchNumber.trim());
    }
}
