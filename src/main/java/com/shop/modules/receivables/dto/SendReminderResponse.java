package com.shop.modules.receivables.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendReminderResponse {
    private boolean success;
    private boolean sent;
    private String whatsappLink;
    private String message;
    private String error;
}
