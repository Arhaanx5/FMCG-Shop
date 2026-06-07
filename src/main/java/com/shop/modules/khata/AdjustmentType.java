package com.shop.modules.khata;

public enum AdjustmentType {
    NORMAL,          // Normal payment ≤ pending amount
    MANUAL_ADJUST,   // Excess manually applied to a specific bill chosen by user
    AUTO_ADJUST      // Excess auto-distributed FIFO across oldest pending bills
}
