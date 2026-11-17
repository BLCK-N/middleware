package com.myrtle.middleware.utils;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Generators {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate Transaction Reference Number
     *
     * @param merchantId 6-character merchant ID (e.g. "000004")
     * @return transaction reference number in format:
     *         MerchantID + yyyyMMdd + random(3 digits, zero-padded)
     */
    public static String generateTrxnRef(String merchantId) {
        String date = LocalDateTime.now().format(DATE_FORMATTER);

        // Random 3-digit number between 001 and 999
        int sequence = RANDOM.nextInt(999) + 1;

        return merchantId + date + String.format("%03d", sequence);
    }


    public static String generateReference() {
        // Format current date as yyyy/MM/dd
        String datePart = new SimpleDateFormat("yyyy/MM/dd").format(new Date());

        // Short unique suffix (first 6 chars of UUID)
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        return "PGW/" + datePart + "/" + uniqueSuffix;
    }

    public static String generateRefCode() {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String datePart = today.format(formatter);

        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 9999); // 4 digits

        return "REF" + datePart + randomSuffix;
    }



}
