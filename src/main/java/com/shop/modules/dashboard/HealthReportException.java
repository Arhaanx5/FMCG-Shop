package com.shop.modules.dashboard;

public class HealthReportException extends RuntimeException {
    
    public HealthReportException(String message) {
        super(message);
    }

    public HealthReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
