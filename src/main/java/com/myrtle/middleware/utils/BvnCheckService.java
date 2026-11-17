package com.myrtle.middleware.utils;

import com.myrtle.middleware.dtos.bvn.BVNRequest;
import com.myrtle.middleware.dtos.bvn.QoreIdBVNResponse;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BvnCheckService {
    public void bvnValidationAlgorithm(QoreIdBVNResponse qoreIdBvnResponse, User user) {

        if (qoreIdBvnResponse == null || qoreIdBvnResponse.getBvn() == null) {
            throw new FunctionException("BVN verification failed. Please confirm that BVN is correct .", null);
        }

        scoreValidations(qoreIdBvnResponse, user.getPhoneNumber(), user.getEmailAddress(),
                trimAndJoin(user.getFirstName(), user.getMiddleName(), user.getLastName()), user.getGender());
    }

    public void bvnValidationAlgorithm(QoreIdBVNResponse qoreIdBvnResponse, BVNRequest request) {
        if (qoreIdBvnResponse == null || qoreIdBvnResponse.getBvn() == null) {
            throw new FunctionException("BVN verification failed. Please confirm that BVN is correct .", null);
        }

        scoreValidations(qoreIdBvnResponse, request.phoneNumber(), request.email(),
                trimAndJoin(request.firstName(), request.lastName()), request.gender());
    }

    private void scoreValidations(QoreIdBVNResponse qoreIdBvnResponse, String phone, String email, String dbFullName, String gender) {
        /*
            - check name
            - check email
            - check phone
            at least 2/3 must pass
         */

        int successCount = 0;

        if (qoreIdBvnResponse.getBvn().getGender().equalsIgnoreCase(gender)) {
            successCount += 2;
        }

        String bvnResponseFullName = trimAndJoin(
                qoreIdBvnResponse.getBvn().getFirstname(),
                qoreIdBvnResponse.getBvn().getMiddlename(),
                qoreIdBvnResponse.getBvn().getLastname()
        );


        boolean checkIfMatches = checkNameMatch(bvnResponseFullName, dbFullName);
        if (!checkIfMatches) {
            log.info("User's name does not match BVN records");
            throw new FunctionException("Your record does not match BVN Credentials", null);
        }
        successCount += 2;
        boolean phoneMatches = validatePhoneMatch(phone, qoreIdBvnResponse.getBvn().getPhone());
        if (phoneMatches) successCount++;

        if (normalizeEmail(email).equalsIgnoreCase(normalizeEmail(qoreIdBvnResponse.getBvn().getEmail()))) {
            successCount++;
        } else {
            log.info("email validation failed");
        }

        if (successCount < 4) {
            throw new FunctionException("BVN does not match your details", null);
        }
        log.info("BVN Verification completed");
    }

    private boolean validatePhoneMatch(String userPhone, String bvnPhone) {
        if (userPhone == null || bvnPhone == null) {
            log.info("phone number validation failed");
            return false;
        }
        if ((userPhone.isBlank() || userPhone.equalsIgnoreCase("NULL")) ||
                (bvnPhone.isBlank() || bvnPhone.equalsIgnoreCase("NULL"))) {
            log.info("phone number validation failed");
            return false;
        }
        return normalize(userPhone).equals(normalize(bvnPhone));
    }


    private String normalize(String phone) {
        if (phone == null) return "";

        String digits = phone.trim();

        boolean hasCountryCode = digits.startsWith("+");

        digits = digits.replaceAll("[^\\d]", "");

        if (hasCountryCode || digits.startsWith("234")) {
            if (digits.startsWith("234")) {
                digits = digits.substring(3);
            }
            if (digits.startsWith("0")) {
                digits = digits.substring(1);
            }
        }

        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        return digits;
    }

    private String trimAndJoin(String... parts) {
        return Stream.of(parts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "))
                .toUpperCase();
    }

    public boolean checkNameMatch(String bvnNameResponse, String nameFromDb) {
        String[] dbNames = nameFromDb.trim().split(" ");
        String[] bvnNames = bvnNameResponse.trim().split(" ");
        boolean[] usedDbNames = new boolean[dbNames.length];
        boolean[] usedBvnNames = new boolean[bvnNames.length];
        int matchCount = 0;

        for (int j = 0; j < bvnNames.length; j++) {
            if (usedBvnNames[j]) continue;
            for (int i = 0; i < dbNames.length; i++) {
                if (!usedDbNames[i] && bvnNames[j].equalsIgnoreCase(dbNames[i])) {
                    usedDbNames[i] = true;
                    usedBvnNames[j] = true;
                    matchCount++;
                    break;
                }
            }
        }

        return matchCount >= 2;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

}
