package com.myrtle.middleware.utils;

public class DeepLinkBuilder {
    public String buildRedirectUrl(double amount, String id, String type, String status) {
        return String.format(
                "myrtlefinancialgroup://payment?amount=%s&id=%s&type=%s&status=%s",
                amount, id, type, status
        );
    }
}
