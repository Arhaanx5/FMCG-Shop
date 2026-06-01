package com.shop.modules.expense;

import com.shop.modules.expense.dto.AddExpenseRequest;
import com.shop.modules.expense.dto.ExpenseResponse;
import com.shop.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    // Convert entity to DTO
    private ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .category(expense.getCategory())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .expenseDate(expense.getExpenseDate())
                .addedBy(expense.getAddedBy() != null
                        ? expense.getAddedBy().getName() : null)
                .createdAt(expense.getCreatedAt())
                .build();
    }

    public List<ExpenseResponse> getAllExpenses() {
        return expenseRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ExpenseResponse> getMonthExpenses(
            int year, int month) {
        return expenseRepository
                .findByYearAndMonth(year, month)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Map<String, BigDecimal> getExpenseSummary(
            int year, int month) {
        return expenseRepository
                .findByYearAndMonth(year, month)
                .stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().name(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Expense::getAmount,
                                BigDecimal::add)));
    }

    public BigDecimal getTotalExpenses(int year, int month) {
        return expenseRepository
                .findByYearAndMonth(year, month)
                .stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public ExpenseResponse addExpense(
            AddExpenseRequest req,
            String addedByPhone) {

        var user = userRepository
                .findByPhone(addedByPhone)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Expense expense = Expense.builder()
                .category(req.getCategory())
                .amount(req.getAmount())
                .description(req.getDescription())
                .expenseDate(req.getExpenseDate())
                .addedBy(user)
                .build();

        return toResponse(expenseRepository.save(expense));
    }

    public void deleteExpense(UUID id) {
        expenseRepository.deleteById(id);
    }
}