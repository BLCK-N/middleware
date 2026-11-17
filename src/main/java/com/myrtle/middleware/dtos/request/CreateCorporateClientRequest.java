package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateCorporateClientRequest(

        @NotBlank(message = "Business structure is required")
        String businessStructure,

        @NotBlank(message = "Business name is required")
        @Size(max = 200, message = "Business name must be at most 200 characters")
        String businessName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Mobile number must be 10-15 digits (optional + prefix)")
        String mobileNo,

        @NotBlank(message = "Valuation currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Valuation currency must be a 3-letter ISO code")
        String valuationCurrency,

        @NotNull(message = "Business address is required")
        @Valid
        AddressPayload businessAddress,

        @NotBlank(message = "Business incorporation date is required")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Business incorporation date must use yyyy-MM-dd format")
        String businessIncDate,

        String idType,

        @NotBlank(message = "ID expiry date is required")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "ID expiry date must use yyyy-MM-dd format")
        String idExpDate,

        @NotBlank(message = "BVN is required")
        @Pattern(regexp = "^[0-9]{11}$", message = "BVN must be exactly 11 digits")
        String bvn,

        @NotBlank(message = "Profession is required")
        String profession,

        @NotBlank(message = "Nationality is required")
        String nationality,

        @NotBlank(message = "PIN is required")
        @Size(min = 4, max = 64, message = "PIN must be 4-64 characters")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String pin,

        @NotNull(message = "Primary contact is required")
        @Valid
        ContactPayload primaryContact,

        List<@Valid ContactPayload> directors,

        @Size(max = 3, message = "Next of kin: at most 3 contacts allowed")
        List<@Valid CorporateNextOfKinPayload> nextOfKins,

        @Pattern(regexp = "^(|[A-Za-z0-9]{4,20})$", message = "inviteCode: use 4-20 letters or numbers")
        String inviteCode
) {

    private static final String CORPORATE_CLIENT_TYPE = "CORPORATE";
    private static final String PRIMARY_ADDRESS_TYPE = "PRIMARY";
    private static final String PRIMARY_CONTACT_LABEL = "Primary Contact";
    private static final String DEFAULT_ID_TYPE = "PASSPORT";
    private static final String DEFAULT_RANGE = "LESS_THAN_100K";

    private static final Set<String> BUSINESS_STRUCTURES = Set.of(
            "MULTI_MEMBER_LLC",
            "SINGLE_MEMBER_LLC",
            "PRIVATE_CORPORATION",
            "PUBLIC_CORPORATION",
            "PARTNERSHIP",
            "SOLE_PROPRIETORSHIP",
            "NON_PROFIT",
            "GOVERNMENT_AGENCY",
            "TRUST",
            "OTHER"
    );
    private static final Set<String> CONTACT_ROLES = Set.of("CORP_DIRECTOR", "BENEFICIAL_OWNER", "NEXT_OF_KIN", "OTHER");
    private static final Set<String> TITLES = Set.of("MR", "MRS", "MISS", "DR", "PROF", "REV", "OTHER_TITLES");
    private static final Set<String> RANGES = Set.of(
            "LESS_THAN_100K",
            "LESS_THAN_250K",
            "LESS_THAN_500K",
            "LESS_THAN_1M",
            "LESS_THAN_5M",
            "LESS_THAN_10M",
            "LESS_THAN_25M",
            "LESS_THAN_50M",
            "LESS_THAN_100M",
            "LESS_THAN_250M",
            "LESS_THAN_500M",
            "LESS_THAN_1B",
            "MORE_THAN_1B"
    );
    private static final Set<String> SOURCES_OF_WEALTH = Set.of(
            "GIFTS",
            "INHERITANCE",
            "BUSINESS",
            "EMPLOYMENT",
            "INVESTMENTS",
            "OTHER_SOURCES",
            "RENT",
            "SAVINGS",
            "PENSION"
    );
    private static final Set<String> INVESTMENT_EXPERIENCES = Set.of("NOVICE", "INTERMEDIATE", "EXPERIENCED");
    private static final Set<String> RISK_TOLERANCES = Set.of(
            "CONSERVATIVE",
            "MODERATE_CONSERVATIVE",
            "MODERATE",
            "MODERATE_AGGRESSIVE",
            "AGGRESSIVE"
    );
    private static final Set<String> INVESTMENT_OBJECTIVES = Set.of(
            "CAPITAL_PRESERVATION",
            "RETIREMENT_INCOME",
            "AGGRESSIVE_GROWTH"
    );
    private static final Set<String> RISK_RATINGS = Set.of("LOW", "MEDIUM", "HIGH");

    public ExternalPayload toExternalRequest(String groupId, String agentId) {
        ContactPayload contact = primaryContact();
        List<ExternalContactPayload> contacts = new ArrayList<>();
        String requestedRole = normalizeContactRole(contact.role());

        if (!"BENEFICIAL_OWNER".equals(requestedRole)) {
            contacts.add(toExternalContact(contact, requestedRole, PRIMARY_CONTACT_LABEL, true));
        }

        boolean hasBeneficialOwner = "BENEFICIAL_OWNER".equals(requestedRole);
        if (directors != null) {
            for (ContactPayload director : directors) {
                if (director == null) {
                    continue;
                }
                String directorRole = normalizeContactRole(
                        director.role() == null || director.role().isBlank() ? "CORP_DIRECTOR" : director.role()
                );
                contacts.add(toExternalContact(director, directorRole, fullName(director), false));
                if ("BENEFICIAL_OWNER".equals(directorRole)) {
                    hasBeneficialOwner = true;
                }
            }
        }

        if (!hasBeneficialOwner) {
            // The primary contact is duplicated as a beneficial owner. It is NOT the login contact
            // here, so its username is role-prefixed - otherwise this copy and the CORP_DIRECTOR
            // above would both send the raw email as username and MyWealth rejects the whole
            // submission for a duplicate username.
            contacts.add(toExternalContact(contact, "BENEFICIAL_OWNER", fullName(contact), false));
        }

        return new ExternalPayload(
                CORPORATE_CLIENT_TYPE,
                normalizeBusinessStructure(businessStructure),
                generateShortRefCode("OMS"),
                businessName,
                null,
                email,
                email,
                mobileNo,
                valuationCurrency,
                groupId,
                List.of(ExternalAddressPayload.from(businessAddress)),
                contacts,
                true,
                businessIncDate,
                true,
                agentId
        );
    }

    private ExternalContactPayload toExternalContact(ContactPayload contact, String role, String label, boolean isLoginContact) {
        return new ExternalContactPayload(
                role,
                firstNonBlank(normalizeOptionalEnumText(contact.maritalStatus()), "SINGLE"),
                normalizeOptionalEnumText(contact.gender()),
                generateShortRefCode("CNT"),
                label,
                normalizeTitle(contact.title()),
                null,
                contact.firstName(),
                contact.middleName(),
                contact.lastName(),
                contact.email(),
                contact.mobileNo(),
                contact.officeNo(),
                ExternalAddressPayload.from(contact.address()),
                resolveContactUsername(role, contact, isLoginContact),
                contact.grantLoginAccess(),
                contact.password(),
                contact.birthDate(),
                firstNonBlank(contact.idNo(), bvn),
                firstNonBlank(contact.finIdNo(), bvn),
                normalizeIdType(idType),
                idExpDate,
                normalizeFreeTextEnum(profession),
                nationality,
                normalizeRange(contact.networthTotalRange()),
                normalizeRange(contact.annualIncomeRange()),
                normalizeRange(contact.networthLiquidRange()),
                normalizeSourceOfWealth(contact.sourceOfWealth()),
                normalizeInvestmentExperience(contact.investmentExperience()),
                normalizeRiskTolerance(contact.riskTolerance()),
                contact.employerName(),
                contact.employerAddress(),
                normalizeInvestmentObjective(contact.investmentObj()),
                false,
                false,
                false,
                normalizeRiskRating("LOW"),
                contact.idIssueDate(),
                contact.motherMaidenName(),
                contact.stateOfOrigin(),
                contact.localGovt(),
                contact.idIssuingAuthority(),
                contact.idPlaceOfIssue(),
                pin
        );
    }

    private static String generateShortRefCode(String prefix) {
        int random = ThreadLocalRandom.current().nextInt(100_000);
        String timestamp = Long.toString(System.currentTimeMillis() % 10_000_000L);
        return prefix + timestamp + String.format("%05d", random);
    }

    /**
     * Only the login contact keeps the raw email as its MyWealth username. Every other contact
     * (directors, beneficial owners, and the beneficial-owner copy of the primary contact) gets a
     * role-prefixed username, so the same person appearing under two roles - the same email on both
     * a CORP_DIRECTOR and a BENEFICIAL_OWNER contact - does not collide on username upstream.
     */
    private static String resolveContactUsername(String role, ContactPayload contact, boolean isLoginContact) {
        String base = firstNonBlank(contact.username(), contact.email());
        if (isLoginContact || base == null) {
            return base;
        }
        String prefix = (role == null || role.isBlank() ? "contact" : role.trim().toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9]+", "-");
        return prefix + "-" + base;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String normalizeBusinessStructure(String value) {
        String normalized = normalizeRequired(value, "businessStructure");
        normalized = switch (normalized) {
            case "LIMITED_LIABILITY_COMPANY", "LLC" -> "MULTI_MEMBER_LLC";
            case "PRIVATE_LIMITED_COMPANY", "LIMITED_COMPANY" -> "PRIVATE_CORPORATION";
            default -> normalized;
        };
        return requireAllowed("businessStructure", normalized, BUSINESS_STRUCTURES);
    }

    private static String normalizeContactRole(String value) {
        String normalized = normalizeRequired(value, "primaryContact.role");
        normalized = switch (normalized) {
            case "DIRECTOR", "PRIMARY_CONTACT", "CORPORATE_DIRECTOR" -> "CORP_DIRECTOR";
            case "OWNER", "SHAREHOLDER", "BENEFICIARY" -> "BENEFICIAL_OWNER";
            default -> normalized;
        };
        return requireAllowed("primaryContact.role", normalized, CONTACT_ROLES);
    }

    private static String normalizeTitle(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeOptionalEnumText(value);
        normalized = switch (normalized) {
            case "MISTER" -> "MR";
            case "MISSUS", "MS" -> "MRS";
            case "PROFESSOR" -> "PROF";
            default -> normalized;
        };
        return requireAllowed("primaryContact.title", normalized, TITLES);
    }

    private static String normalizeRange(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_RANGE;
        }
        String normalized = normalizeOptionalEnumText(value);
        if (RANGES.contains(normalized)) {
            return normalized;
        }

        String digits = value.replaceAll("[^0-9-]", "");
        if (!digits.isBlank()) {
            String[] parts = digits.split("-");
            String candidate = parts.length > 1 ? parts[parts.length - 1] : parts[0];
            try {
                long amount = Long.parseLong(candidate);
                if (amount <= 100_000L) return "LESS_THAN_100K";
                if (amount <= 250_000L) return "LESS_THAN_250K";
                if (amount <= 500_000L) return "LESS_THAN_500K";
                if (amount <= 1_000_000L) return "LESS_THAN_1M";
                if (amount <= 5_000_000L) return "LESS_THAN_5M";
                if (amount <= 10_000_000L) return "LESS_THAN_10M";
                if (amount <= 25_000_000L) return "LESS_THAN_25M";
                if (amount <= 50_000_000L) return "LESS_THAN_50M";
                if (amount <= 100_000_000L) return "LESS_THAN_100M";
                if (amount <= 250_000_000L) return "LESS_THAN_250M";
                if (amount <= 500_000_000L) return "LESS_THAN_500M";
                if (amount <= 1_000_000_000L) return "LESS_THAN_1B";
                return "MORE_THAN_1B";
            } catch (NumberFormatException ignored) {
                // Fall through to validation error below.
            }
        }

        return requireAllowed("wealth range", normalized, RANGES);
    }

    private static String normalizeSourceOfWealth(String value) {
        if (value == null || value.isBlank()) {
            return "OTHER_SOURCES";
        }
        String normalized = normalizeOptionalEnumText(value);
        normalized = switch (normalized) {
            case "BUSINESS_INCOME", "BUSINESS_OWNER" -> "BUSINESS";
            case "EMPLOYMENT_INCOME", "SALARY" -> "EMPLOYMENT";
            case "INVESTMENT", "INVESTMENT_INCOME" -> "INVESTMENTS";
            case "OTHER" -> "OTHER_SOURCES";
            default -> normalized;
        };
        return requireAllowed("primaryContact.sourceOfWealth", normalized, SOURCES_OF_WEALTH);
    }

    private static String normalizeInvestmentExperience(String value) {
        if (value == null || value.isBlank()) {
            return "NOVICE";
        }
        String normalized = normalizeOptionalEnumText(value);
        normalized = switch (normalized) {
            case "BEGINNER" -> "NOVICE";
            case "MODERATE" -> "INTERMEDIATE";
            case "ADVANCED", "EXPERT" -> "EXPERIENCED";
            default -> normalized;
        };
        return requireAllowed("primaryContact.investmentExperience", normalized, INVESTMENT_EXPERIENCES);
    }

    private static String normalizeRiskTolerance(String value) {
        if (value == null || value.isBlank()) {
            return "MODERATE";
        }
        String normalized = normalizeOptionalEnumText(value);
        normalized = switch (normalized) {
            case "LOW" -> "CONSERVATIVE";
            case "MEDIUM" -> "MODERATE";
            case "HIGH" -> "AGGRESSIVE";
            default -> normalized;
        };
        return requireAllowed("primaryContact.riskTolerance", normalized, RISK_TOLERANCES);
    }

    private static String normalizeInvestmentObjective(String value) {
        if (value == null || value.isBlank()) {
            return "CAPITAL_PRESERVATION";
        }
        String normalized = normalizeOptionalEnumText(value);
        normalized = switch (normalized) {
            case "CAPITAL_GROWTH", "GROWTH" -> "AGGRESSIVE_GROWTH";
            case "PRESERVATION" -> "CAPITAL_PRESERVATION";
            case "RETIREMENT" -> "RETIREMENT_INCOME";
            default -> normalized;
        };
        return requireAllowed("primaryContact.investmentObj", normalized, INVESTMENT_OBJECTIVES);
    }

    private static String normalizeRiskRating(String value) {
        String normalized = normalizeRequired(value, "riskRating");
        return requireAllowed("riskRating", normalized, RISK_RATINGS);
    }

    /**
     * Runs the same enum normalisation that {@link #toExternalRequest(String)} performs for the
     * optional financial-profile fields, so an unrecognised value is rejected the moment it is
     * submitted (at the credentials step) instead of only surfacing at completion. Only non-blank
     * values are checked - blank ones default at completion and never fail. Throws
     * {@link IllegalArgumentException} with the offending field, matching the completion-time error.
     */
    public static void validateProfileFields(
            String businessStructure,
            String title,
            String sourceOfWealth,
            String investmentExperience,
            String riskTolerance,
            String investmentObjective,
            String netWorthRange,
            String annualIncomeRange,
            String liquidNetWorthRange
    ) {
        if (hasText(businessStructure)) {
            normalizeBusinessStructure(businessStructure);
        }
        if (hasText(title)) {
            normalizeTitle(title);
        }
        if (hasText(sourceOfWealth)) {
            normalizeSourceOfWealth(sourceOfWealth);
        }
        if (hasText(investmentExperience)) {
            normalizeInvestmentExperience(investmentExperience);
        }
        if (hasText(riskTolerance)) {
            normalizeRiskTolerance(riskTolerance);
        }
        if (hasText(investmentObjective)) {
            normalizeInvestmentObjective(investmentObjective);
        }
        if (hasText(netWorthRange)) {
            normalizeRange(netWorthRange);
        }
        if (hasText(annualIncomeRange)) {
            normalizeRange(annualIncomeRange);
        }
        if (hasText(liquidNetWorthRange)) {
            normalizeRange(liquidNetWorthRange);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeIdType(String value) {
        if (value == null || value.isBlank() || "BVN".equalsIgnoreCase(value.trim())) {
            return DEFAULT_ID_TYPE;
        }
        return normalizeOptionalEnumText(value);
    }

    private static String normalizeFreeTextEnum(String value) {
        return value == null ? null : normalizeOptionalEnumText(value);
    }

    private static String normalizeOptionalEnumText(String value) {
        return value == null ? null : value.trim()
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptionalEnumText(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String requireAllowed(String fieldName, String value, Set<String> allowedValues) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException("Invalid " + fieldName + ". Allowed values: " + allowedValues);
        }
        return value;
    }

    private static String fullName(ContactPayload contact) {
        String value = String.join(" ", Arrays.stream(new String[]{contact.firstName(), contact.middleName(), contact.lastName()})
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .toList());
        return value.isBlank() ? PRIMARY_CONTACT_LABEL : value;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressPayload(
            @NotBlank(message = "Address line 1 is required")
            @Size(max = 150, message = "Address line 1 must be at most 150 characters")
            String addressLine1,

            @Size(max = 150, message = "Address line 2 must be at most 150 characters")
            String addressLine2,

            @NotBlank(message = "City is required")
            @Size(max = 100, message = "City must be at most 100 characters")
            String city,

            @NotBlank(message = "State is required")
            @Size(max = 100, message = "State must be at most 100 characters")
            String state,

            @NotBlank(message = "Post code is required")
            @Size(max = 20, message = "Post code must be at most 20 characters")
            String postCode,

            @NotBlank(message = "Country is required")
            @Size(max = 100, message = "Country must be at most 100 characters")
            String country
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContactAddressPayload(
            @NotBlank(message = "Address line 1 is required")
            @Size(max = 150, message = "Address line 1 must be at most 150 characters")
            String addressLine1,

            @Size(max = 150, message = "Address line 2 must be at most 150 characters")
            String addressLine2,

            @NotBlank(message = "City is required")
            @Size(max = 100, message = "City must be at most 100 characters")
            String city,

            @NotBlank(message = "State is required")
            @Size(max = 100, message = "State must be at most 100 characters")
            String state,

            @NotBlank(message = "Post code is required")
            @Size(max = 20, message = "Post code must be at most 20 characters")
            String postCode,

            @NotBlank(message = "Country is required")
            @Size(max = 100, message = "Country must be at most 100 characters")
            String country
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContactPayload(
            @NotBlank(message = "Contact role is required")
            String role,

            String maritalStatus,
            String gender,
            String title,

            @NotBlank(message = "First name is required")
            @Size(max = 80, message = "First name must be at most 80 characters")
            String firstName,

            @Size(max = 80, message = "Middle name must be at most 80 characters")
            String middleName,

            @NotBlank(message = "Last name is required")
            @Size(max = 80, message = "Last name must be at most 80 characters")
            String lastName,

            @NotBlank(message = "Contact email is required")
            @Email(message = "Contact email must be valid")
            @Size(max = 150, message = "Contact email must be at most 150 characters")
            String email,

            @NotBlank(message = "Contact mobile number is required")
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Contact mobile number must be 10-15 digits (optional + prefix)")
            String mobileNo,

            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Contact office number must be 10-15 digits (optional + prefix)")
            String officeNo,

            @NotNull(message = "Contact address is required")
            @Valid
            ContactAddressPayload address,

            String username,
            Boolean grantLoginAccess,

            @Size(min = 8, max = 64, message = "Password must be 8-64 characters")
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            String password,

            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Birth date must use yyyy-MM-dd format")
            String birthDate,

            @Size(max = 50, message = "ID number must be at most 50 characters")
            String idNo,

            @Pattern(regexp = "^[0-9]{11}$", message = "Financial ID number must be exactly 11 digits")
            String finIdNo,

            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "ID issue date must use yyyy-MM-dd format")
            String idIssueDate,
            String idIssuingAuthority,
            String idPlaceOfIssue,
            String motherMaidenName,
            String stateOfOrigin,
            String localGovt,
            String networthTotalRange,
            String annualIncomeRange,
            String networthLiquidRange,
            String sourceOfWealth,
            String investmentExperience,
            String riskTolerance,
            String investmentObj,
            String employerName,
            String employerAddress
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CorporateNextOfKinPayload(
            @NotBlank(message = "NOK first name is required")
            @Size(max = 80, message = "NOK first name must be at most 80 characters")
            String firstName,

            @Size(max = 80, message = "NOK middle name must be at most 80 characters")
            String middleName,

            @NotBlank(message = "NOK last name is required")
            @Size(max = 80, message = "NOK last name must be at most 80 characters")
            String lastName,

            @NotBlank(message = "NOK relationship is required")
            String relationship,

            @Email(message = "NOK email must be valid")
            @Size(max = 150, message = "NOK email must be at most 150 characters")
            String email,

            @NotBlank(message = "NOK mobile number is required")
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "NOK mobile number must be 10-15 digits (optional + prefix)")
            String mobileNo,

            @NotBlank(message = "NOK address is required")
            @Size(max = 255, message = "NOK address must be at most 255 characters")
            String address,

            Boolean isPrimary
    ) {
        public String fullName() {
            String value = String.join(" ", Arrays.stream(new String[]{firstName, middleName, lastName})
                    .filter(part -> part != null && !part.isBlank())
                    .map(String::trim)
                    .toList());
            return value.isBlank() ? null : value;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalPayload(
            String clientType,
            String businessStructure,
            String refCode,
            String label,
            String notes,
            String email,
            String notificationEmail,
            String mobileNo,
            String valuationCurrency,
            String groupId,
            List<ExternalAddressPayload> address,
            List<ExternalContactPayload> contact,
            Boolean autoApprove,
            String businessIncDate,
            Boolean notificationEnabled,
            String agentId
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalAddressPayload(
            String type,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postCode,
            String country
    ) {
        private static ExternalAddressPayload from(AddressPayload address) {
            if (address == null) {
                return null;
            }
            return new ExternalAddressPayload(
                    PRIMARY_ADDRESS_TYPE,
                    address.addressLine1(),
                    address.addressLine2(),
                    address.city(),
                    address.state(),
                    address.postCode(),
                    normalizeCountryCode(address.country())
            );
        }

        private static ExternalAddressPayload from(ContactAddressPayload address) {
            if (address == null) {
                return null;
            }
            return new ExternalAddressPayload(
                    PRIMARY_ADDRESS_TYPE,
                    address.addressLine1(),
                    address.addressLine2(),
                    address.city(),
                    address.state(),
                    address.postCode(),
                    normalizeCountryCode(address.country())
            );
        }

        private static String normalizeCountryCode(String country) {
            if (country == null || country.isBlank()) {
                return "NG";
            }
            String trimmed = country.trim();
            if (trimmed.length() <= 3) {
                return trimmed.toUpperCase(Locale.ROOT);
            }
            if ("NIGERIA".equalsIgnoreCase(trimmed) || "NIGERIAN".equalsIgnoreCase(trimmed)) {
                return "NG";
            }
            return trimmed.substring(0, 2).toUpperCase(Locale.ROOT);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalContactPayload(
            String role,
            String maritalStatus,
            String gender,
            String refCode,
            String label,
            String title,
            String notes,
            String firstName,
            String middleName,
            String lastName,
            String email,
            String mobileNo,
            String officeNo,
            ExternalAddressPayload address,
            String username,
            Boolean grantLoginAccess,
            String password,
            String birthDate,
            String idNo,
            String finIdNo,
            String idType,
            String idExpDate,
            String profession,
            String nationality,
            String networthTotalRange,
            String annualIncomeRange,
            String networthLiquidRange,
            String sourceOfWealth,
            String investmentExperience,
            String riskTolerance,
            String employerName,
            String employerAddress,
            String investmentObj,
            Boolean moneyLaunderingRisk,
            Boolean politicallyExposed,
            Boolean criminalConviction,
            String riskRating,
            String idIssueDate,
            String motherMaidenName,
            String stateOfOrigin,
            String localGovt,
            String idIssuingAuthority,
            String idPlaceOfIssue,
            String pin
    ) {
    }
}
