package com.myrtle.middleware.utils;

import com.myrtle.middleware.dtos.request.CreateCorporateClientRequest;
import com.myrtle.middleware.dtos.request.CreateCorporateClientRequest.AddressPayload;
import com.myrtle.middleware.dtos.request.CreateCorporateClientRequest.ContactAddressPayload;
import com.myrtle.middleware.dtos.request.CreateCorporateClientRequest.ContactPayload;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.PendingCorporateDirector;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps staged corporate onboarding data into a MyWealth {@link CreateCorporateClientRequest}.
 */
public final class PendingCorporateOnboardingMapper {

    private static final String DEFAULT_CURRENCY = "NGN";
    private static final String DEFAULT_NATIONALITY = "Nigerian";
    private static final String DEFAULT_PROFESSION = "DIRECTOR";
    private static final String DEFAULT_COUNTRY = "NG";
    private static final String DEFAULT_MARITAL_STATUS = "SINGLE";
    private static final String DEFAULT_POST_CODE = "000000";
    private static final String DEFAULT_BUSINESS_STRUCTURE = "PRIVATE_CORPORATION";
    private static final String DEFAULT_ID_EXPIRY = "2099-12-31";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Map<String, String> COMPANY_TYPE_TO_STRUCTURE = Map.ofEntries(
            Map.entry("LIMITED LIABILITY COMPANY", "MULTI_MEMBER_LLC"),
            Map.entry("LLC", "MULTI_MEMBER_LLC"),
            Map.entry("PRIVATE LIMITED COMPANY", "PRIVATE_CORPORATION"),
            Map.entry("PRIVATE_COMPANY_LIMITED_BY_SHARES", "PRIVATE_CORPORATION"),
            Map.entry("LIMITED COMPANY", "PRIVATE_CORPORATION"),
            Map.entry("PUBLIC LIMITED COMPANY", "PUBLIC_CORPORATION"),
            Map.entry("PUBLIC_COMPANY_LIMITED_BY_SHARES", "PUBLIC_CORPORATION"),
            Map.entry("PARTNERSHIP", "PARTNERSHIP"),
            Map.entry("SOLE PROPRIETORSHIP", "SOLE_PROPRIETORSHIP"),
            Map.entry("BUSINESS NAME", "SOLE_PROPRIETORSHIP"),
            Map.entry("INCORPORATED TRUSTEES", "NON_PROFIT"),
            Map.entry("TRUST", "TRUST")
    );

    private PendingCorporateOnboardingMapper() {
    }

    public static CreateCorporateClientRequest toCreateCorporateClientRequest(PendingCorporateData pending) {
        require(pending);
        requireText(pending.getCompanyName(), "Company name is required before creating a MyWealth corporate client");
        requireText(pending.getEmail(), "Representative email is required");
        requireText(pending.getPhoneNumber(), "Representative phone number is required");
        requireText(pending.getBvn(), "Representative BVN is required");
        requireText(pending.getPin(), "PIN is required");
        requireText(pending.getPassword(), "Password is required");
        requireText(pending.getFirstName(), "Representative first name is required");
        requireText(pending.getLastName(), "Representative last name is required");

        String businessIncDate = resolveBusinessIncDate(pending);

        AddressPayload businessAddress = toBusinessAddress(pending);
        ContactAddressPayload contactAddress = toContactAddress(pending, null);
        if (contactAddress == null) {
            contactAddress = toContactAddressFromBusiness(businessAddress);
        }

        ContactPayload primaryContact = new ContactPayload(
                "CORP_DIRECTOR",
                resolveMaritalStatus(pending.getMaritalStatus()),
                pending.getGender() == null ? null : pending.getGender().name(),
                pending.getTitle(),
                pending.getFirstName(),
                pending.getMiddleName(),
                pending.getLastName(),
                pending.getEmail(),
                pending.getPhoneNumber(),
                null,
                contactAddress,
                pending.getEmail(),
                true,
                pending.getPassword(),
                businessIncDate,
                pending.getBvn(),
                pending.getBvn(),
                pending.getIdIssue(),
                pending.getIssuingAuthority(),
                pending.getPlaceOfIssue(),
                pending.getMothersMaidenName(),
                pending.getStateOfOrigin(),
                pending.getLocalGovernment(),
                pending.getNetWorth(),
                pending.getAnnualIncome(),
                pending.getLiquidNetWorth(),
                pending.getSourceOfWealth(),
                pending.getInvestmentExperience(),
                pending.getRiskTolerance(),
                pending.getInvestmentObjective(),
                pending.getEmployerName(),
                firstNonBlank(pending.getEmployerAddress(), pending.getHeadOfficeAddress())
        );

        List<ContactPayload> directors = mapDirectors(pending, businessAddress, businessIncDate);

        return new CreateCorporateClientRequest(
                resolveBusinessStructure(pending),
                pending.getCompanyName(),
                firstNonBlank(pending.getCompanyEmail(), pending.getEmail()),
                pending.getPhoneNumber(),
                firstNonBlank(pending.getValuationCurrency(), DEFAULT_CURRENCY),
                businessAddress,
                businessIncDate,
                pending.getIdType() == null ? "PASSPORT" : pending.getIdType().name(),
                firstNonBlank(pending.getIdExpiry(), DEFAULT_ID_EXPIRY),
                pending.getBvn(),
                firstNonBlank(pending.getProfession(), DEFAULT_PROFESSION),
                firstNonBlank(pending.getNationality(), DEFAULT_NATIONALITY),
                pending.getPin(),
                primaryContact,
                directors,
                List.of(),
                pending.getInviteCode()
        );
    }

    private static List<ContactPayload> mapDirectors(PendingCorporateData pending, AddressPayload businessAddress, String businessIncDate) {
        List<PendingCorporateDirector> source = pending.getDirectors();
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        List<ContactPayload> directors = new ArrayList<>();
        for (PendingCorporateDirector director : source) {
            if (director == null) {
                continue;
            }
            ContactAddressPayload address = toContactAddress(pending, director);
            if (address == null) {
                address = new ContactAddressPayload(
                        businessAddress.addressLine1(),
                        businessAddress.addressLine2(),
                        businessAddress.city(),
                        businessAddress.state(),
                        businessAddress.postCode(),
                        businessAddress.country()
                );
            }

            String role = firstNonBlank(director.getRole(), "CORP_DIRECTOR");
            if (director.isBeneficialOwner() && !"BENEFICIAL_OWNER".equalsIgnoreCase(role)) {
                role = "CORP_DIRECTOR";
            }

            String directorBirthDate = firstNonBlank(normalizeBirthDate(director.getBirthDate()), businessIncDate);

            boolean isPrimaryContact = pending.getBvn() != null && pending.getBvn().equals(director.getBvn());

            boolean isThinDuplicateDirector = "CORP_DIRECTOR".equalsIgnoreCase(role)
                    && !StringUtils.hasText(director.getTitle())
                    && !StringUtils.hasText(director.getGender());

            ContactPayload payload = new ContactPayload(
                    role,
                    resolveMaritalStatus(director.getMaritalStatus()),
                    director.getGender(),
                    director.getTitle(),
                    director.getFirstName(),
                    director.getMiddleName(),
                    director.getLastName(),
                    director.getEmail(),
                    director.getPhoneNumber(),
                    null,
                    address,
                    director.getEmail(),
                    false,
                    null,
                    directorBirthDate,
                    director.getBvn(),
                    director.getBvn(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            if (!isPrimaryContact && !isThinDuplicateDirector) {
                directors.add(payload);
            }

            if (director.isBeneficialOwner()) {
                directors.add(new ContactPayload(
                        "BENEFICIAL_OWNER",
                        payload.maritalStatus(),
                        payload.gender(),
                        payload.title(),
                        payload.firstName(),
                        payload.middleName(),
                        payload.lastName(),
                        payload.email(),
                        payload.mobileNo(),
                        payload.officeNo(),
                        payload.address(),
                        payload.email(),
                        false,
                        null,
                        payload.birthDate(),
                        payload.idNo(),
                        payload.finIdNo(),
                        payload.idIssueDate(),
                        payload.idIssuingAuthority(),
                        payload.idPlaceOfIssue(),
                        payload.motherMaidenName(),
                        payload.stateOfOrigin(),
                        payload.localGovt(),
                        payload.networthTotalRange(),
                        payload.annualIncomeRange(),
                        payload.networthLiquidRange(),
                        payload.sourceOfWealth(),
                        "NOVICE",
                        payload.riskTolerance(),
                        payload.investmentObj(),
                        payload.employerName(),
                        payload.employerAddress()
                ));
            }
        }
        return directors;
    }


    private static AddressPayload toBusinessAddress(PendingCorporateData pending) {
        String line1 = firstNonBlank(pending.getHeadOfficeAddress(), pending.getBranchAddress(), pending.getAddress());
        String city = firstNonBlank(pending.getCity(), pending.getLga(), pending.getState());
        String state = firstNonBlank(pending.getState(), pending.getLga());

        requireText(line1, "Business address is required");
        requireText(city, "Business city is required. Complete KYB or ensure CAC data includes city, LGA, or state.");
        requireText(state, "Business state is required. Complete KYB or ensure CAC data includes state or LGA.");

        return new AddressPayload(
                truncate(line1, 150),
                truncate(pending.getAddressLine2(), 150),
                truncate(city, 100),
                truncate(state, 100),
                firstNonBlank(pending.getPostCode(), DEFAULT_POST_CODE),
                normalizeCountryCode(firstNonBlank(pending.getCountry(), DEFAULT_COUNTRY))
        );
    }


    private static ContactAddressPayload toContactAddressFromBusiness(AddressPayload businessAddress) {
        return new ContactAddressPayload(
                businessAddress.addressLine1(),
                businessAddress.addressLine2(),
                businessAddress.city(),
                businessAddress.state(),
                businessAddress.postCode(),
                businessAddress.country()
        );
    }


    private static ContactAddressPayload toContactAddress(PendingCorporateData pending, PendingCorporateDirector director) {
        if (director != null && StringUtils.hasText(director.getAddressLine1())) {
            String city = firstNonBlank(director.getCity(), pending.getCity(), pending.getLga(), pending.getState());
            String state = firstNonBlank(director.getState(), pending.getState(), pending.getLga());
            if (!StringUtils.hasText(city) || !StringUtils.hasText(state)) {
                return null;
            }
            return new ContactAddressPayload(
                    truncate(director.getAddressLine1(), 150),
                    truncate(director.getAddressLine2(), 150),
                    truncate(city, 100),
                    truncate(state, 100),
                    firstNonBlank(director.getPostCode(), pending.getPostCode(), DEFAULT_POST_CODE),
                    normalizeCountryCode(firstNonBlank(director.getCountry(), pending.getCountry(), DEFAULT_COUNTRY))
            );
        }

        String line1 = firstNonBlank(pending.getAddress(), pending.getHeadOfficeAddress(), pending.getBranchAddress());
        String city = firstNonBlank(pending.getCity(), pending.getLga(), pending.getState());
        String state = firstNonBlank(pending.getState(), pending.getLga());
        if (!StringUtils.hasText(line1) || !StringUtils.hasText(city) || !StringUtils.hasText(state)) {
            return null;
        }

        return new ContactAddressPayload(
                truncate(line1, 150),
                truncate(pending.getAddressLine2(), 150),
                truncate(city, 100),
                truncate(state, 100),
                firstNonBlank(pending.getPostCode(), DEFAULT_POST_CODE),
                normalizeCountryCode(firstNonBlank(pending.getCountry(), DEFAULT_COUNTRY))
        );
    }


    public static String normalizeBirthDate(String rawBirthDate) {
        if (!StringUtils.hasText(rawBirthDate)) {
            return null;
        }

        String value = rawBirthDate.trim();
        List<DateTimeFormatter> supportedFormats = List.of(
                DATE,
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM-dd-yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
        );

        for (DateTimeFormatter formatter : supportedFormats) {
            try {
                return LocalDate.parse(value, formatter).format(DATE);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }


    public static String normalizeCountryCode(String country) {
        if (!StringUtils.hasText(country)) {
            return DEFAULT_COUNTRY;
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


    private static String resolveMaritalStatus(String maritalStatus) {
        if (!StringUtils.hasText(maritalStatus)) {
            return DEFAULT_MARITAL_STATUS;
        }
        String normalized = maritalStatus.trim().toUpperCase(Locale.ROOT);
        if ("SINGLE".equals(normalized) || "MARRIED".equals(normalized)) {
            return normalized;
        }
        return DEFAULT_MARITAL_STATUS;
    }


    private static String resolveBusinessStructure(PendingCorporateData pending) {
        if (StringUtils.hasText(pending.getBusinessStructure())) {
            return pending.getBusinessStructure().trim();
        }
        String companyType = pending.getCompanyType();
        if (StringUtils.hasText(companyType)) {
            String normalized = companyType.trim().toUpperCase(Locale.ROOT);
            String mapped = COMPANY_TYPE_TO_STRUCTURE.get(normalized);
            if (mapped != null) {
                return mapped;
            }
            for (Map.Entry<String, String> entry : COMPANY_TYPE_TO_STRUCTURE.entrySet()) {
                if (normalized.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return DEFAULT_BUSINESS_STRUCTURE;
    }


    private static String resolveBusinessIncDate(PendingCorporateData pending) {
        OffsetDateTime registrationDate = pending.getRegistrationDate();
        if (registrationDate != null) {
            return registrationDate.toLocalDate().format(DATE);
        }
        throw new FunctionException(
                "Business incorporation date is required before creating a MyWealth corporate client",
                null
        );
    }


    private static void require(Object value) {
        if (value == null) {
            throw new FunctionException("Pending corporate data is required", null);
        }
    }


    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new FunctionException(message, null);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }


    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
