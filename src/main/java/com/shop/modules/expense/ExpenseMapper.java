package com.shop.modules.expense;

import com.shop.modules.expense.dto.ExpenseResponse;
import org.springframework.stereotype.Component;

@Component
public class ExpenseMapper {

    public ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .category(expense.getCategory())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .expenseDate(expense.getExpenseDate())
                .addedBy(expense.getAddedBy() != null ? expense.getAddedBy().getName() : null)
                .recipientId(expense.getRecipient() != null ? expense.getRecipient().getId() : null)
                .recipientName(expense.getRecipient() != null ? expense.getRecipient().getName() : null)
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
