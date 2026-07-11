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
    private final ExpenseMapper expenseMapper;

    public List<ExpenseResponse> getAllExpenses() {
        return expenseRepository.findAll()
                .stream()
                .map(expenseMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<ExpenseResponse> getMonthExpenses(
            int year, int month) {
        return expenseRepository
                .findByYearAndMonth(year, month)
                .stream()
                .map(expenseMapper::toResponse)
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

        com.shop.modules.user.User recipient = null;
        if (req.getRecipientId() != null) {
            recipient = userRepository.findById(req.getRecipientId())
                    .orElse(null);
        }

        Expense expense = Expense.builder()
                .category(req.getCategory())
                .amount(req.getAmount())
                .description(req.getDescription())
                .expenseDate(req.getExpenseDate())
                .addedBy(user)
                .recipient(recipient)
                .build();

        return expenseMapper.toResponse(expenseRepository.save(expense));

    }

    public void deleteExpense(UUID id) {
        expenseRepository.deleteById(id);
    }
}