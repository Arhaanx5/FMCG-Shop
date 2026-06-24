package com.shop.modules.stock;

import com.shop.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock/movements")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockMovementService movementService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Page<StockMovement>>> getMovements(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay().minusNanos(1) : null;
        String type = (movementType != null && !movementType.trim().isEmpty()) ? movementType.trim() : null;
        String searchStr = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<StockMovement> pageData = movementService.getFilteredMovements(
                productId, type, start, end, searchStr, PageRequest.of(page, size)
        );

        return ResponseEntity.ok(ApiResponse.success(pageData));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void exportMovements(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay().minusNanos(1) : null;
        String type = (movementType != null && !movementType.trim().isEmpty()) ? movementType.trim() : null;
        String searchStr = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        List<StockMovement> movements = movementService.getAllFilteredMovements(productId, type, start, end, searchStr);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=stock_movements.csv");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        java.io.PrintWriter writer = response.getWriter();
        
        // Write CSV Header
        writer.println("Timestamp,Product,Batch,Movement Type,Quantity Change,Old Stock,New Stock,Unit Cost,Total Value,User,Reference,Remarks");

        for (StockMovement sm : movements) {
            String timestamp = sm.getTimestamp() != null ? sm.getTimestamp().format(dtf) : "";
            String product = sm.getProduct() != null ? sm.getProduct().getName() : "";
            String batch = sm.getBatch() != null ? sm.getBatch().getBatchNumber() : "";
            String typeCol = sm.getMovementType() != null ? sm.getMovementType() : "";
            String qtyChange = sm.getQuantity() != null ? sm.getQuantity().toString() : "0";
            String oldStock = sm.getQuantityBefore() != null ? sm.getQuantityBefore().toString() : "";
            String newStock = sm.getQuantityAfter() != null ? sm.getQuantityAfter().toString() : "";
            String unitCost = sm.getUnitPrice() != null ? sm.getUnitPrice().toString() : "0.00";
            String totalValue = sm.getTotalValue() != null ? sm.getTotalValue().toString() : "0.00";
            String user = sm.getUsername() != null ? sm.getUsername() : "";
            String reference = sm.getReferenceNumber() != null ? sm.getReferenceNumber() : "";
            String remarks = sm.getRemarks() != null ? sm.getRemarks() : "";

            writer.println(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\",\"%s\",%s,%s,\"%s\",\"%s\",\"%s\"",
                    escapeCsv(timestamp), escapeCsv(product), escapeCsv(batch), escapeCsv(typeCol),
                    qtyChange, escapeCsv(oldStock), escapeCsv(newStock), unitCost, totalValue,
                    escapeCsv(user), escapeCsv(reference), escapeCsv(remarks)));
        }
        writer.flush();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}
