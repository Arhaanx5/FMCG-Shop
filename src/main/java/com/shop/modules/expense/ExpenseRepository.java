package com.shop.modules.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @Query("SELECT e FROM Expense e WHERE " +
            "e.expenseDate BETWEEN :start AND :end " +
            "ORDER BY e.expenseDate DESC")
    List<Expense> findBetween(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT e FROM Expense e WHERE " +
            "YEAR(e.expenseDate) = :year AND " +
            "MONTH(e.expenseDate) = :month")
    List<Expense> findByYearAndMonth(
            @Param("year") int year,
            @Param("month") int month);
}