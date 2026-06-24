package com.shop.modules.khata.dto;

import com.shop.modules.khata.AdjustmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RecordPaymentRequest {

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    private UUID billId;

    private BigDecimal amount;

    @NotNull(message = "Payment mode is required")
    private String paymentMode;

    private String notes;
    private String paymentSource;
    private BigDecimal waivedAmount;

    // ── Overpayment resolution fields (sent only when excess is detected) ──
    /** How to handle excess: MANUAL_ADJUST or AUTO_ADJUST */
    private AdjustmentType adjustmentType;

    /** For MANUAL_ADJUST: the other bill chosen by the user to receive the excess */
    private UUID targetBillId;

    /** Safety flag — frontend must set true after user explicitly confirms */
    private boolean confirmedByUser = false;
}