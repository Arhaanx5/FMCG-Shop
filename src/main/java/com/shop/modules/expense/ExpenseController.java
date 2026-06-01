package com.shop.modules.expense;

import com.shop.modules.expense.dto.AddExpenseRequest;
import com.shop.modules.expense.dto.ExpenseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ExpenseResponse> getAll() {
        return expenseService.getAllExpenses();
    }

    @GetMapping("/month")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ExpenseResponse> getMonthExpenses(
            @RequestParam int year,
            @RequestParam int month) {
        return expenseService.getMonthExpenses(year, month);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, BigDecimal> getSummary(
            @RequestParam int year,
            @RequestParam int month) {
        return expenseService.getExpenseSummary(year, month);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ExpenseResponse add(
            @Valid @RequestBody AddExpenseRequest req,
            Authentication auth) {
        return expenseService.addExpense(req, auth.getName());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(
            @PathVariable UUID id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.ok("Expense deleted");
    }
}