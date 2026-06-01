package com.shop.modules.expense.dto;

import com.shop.modules.expense.ExpenseCategory;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ExpenseResponse {

    private UUID id;
    private ExpenseCategory category;
    private BigDecimal amount;
    private String description;
    private LocalDate expenseDate;
    private String addedBy;
    private LocalDateTime createdAt;
}