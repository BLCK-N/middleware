package com.myrtle.middleware.services.familyoffice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myrtle.middleware.dtos.request.FamilyOfficeOnboardingRequest;
import com.myrtle.middleware.dtos.request.PersonalInformationRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.DocumentCheckResponse;
import com.myrtle.middleware.dtos.response.FamilyInformationResponse;
import com.myrtle.middleware.dtos.response.FamilyOfficeOnboardingResponse;
import com.myrtle.middleware.dtos.response.FinancialWorldResponse;
import com.myrtle.middleware.dtos.response.FutureProtectionResponse;
import com.myrtle.middleware.dtos.response.ImportantRelationshipResponse;
import com.myrtle.middleware.dtos.response.PersonalInformationResponse;
import com.myrtle.middleware.dtos.response.WealthJourneyResponse;
import com.myrtle.middleware.enums.MaritalStatus;
import com.myrtle.middleware.enums.PriorityArea;
import com.myrtle.middleware.model.FamilyOfficeOnboarding;
import com.myrtle.middleware.repository.FamilyOfficeOnboardingRepository;
import com.myrtle.middleware.services.OpenAiService;
import com.myrtle.middleware.specification.FamilyOfficeOnboardingSpecification;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyOfficeOnboardingService {

    private static final String CREATE_MESSAGE = "Thank you for sharing your family's story with us. Your personalised Family Wealth Clarity Report is being prepared from your responses and will be delivered to your inbox in just a few moments.";
    private static final String FETCH_ALL_MESSAGE = "Family office onboarding responses retrieved successfully";
    private static final String FETCH_SINGLE_MESSAGE = "Family office onboarding response retrieved successfully";
    private static final String UPDATE_MESSAGE = "Family office onboarding response updated successfully";
    private static final String DELETE_MESSAGE = "Family office onboarding response deleted successfully";
    private static final String NOT_FOUND_MESSAGE = "Family office onboarding response not found";

    private final FamilyOfficeOnboardingRepository familyOfficeOnboardingRepository;
    private final ObjectMapper objectMapper;
    private final ResponseUtil responseUtil;
    private final OpenAiService openAiService;

    public ResponseEntity<?> create(FamilyOfficeOnboardingRequest request) {
        String recipientEmail = request.getPersonalInformation().getEmail();
        log.info("Creating family office onboarding | email={}", recipientEmail);

        FamilyOfficeOnboarding entity = new FamilyOfficeOnboarding();
        applyRequest(entity, request);

        familyOfficeOnboardingRepository.save(entity);
        log.info("Family office onboarding saved | id={} | email={}", entity.getId(), recipientEmail);

        ResponseEntity<?> response = responseUtil.success(CREATE_MESSAGE, null);

        log.debug("Dispatching async family office report generation | email={}", recipientEmail);
        openAiService.generateAndSendReportAsync(recipientEmail, request);

        return response;
    }

    public ResponseEntity<ApiResponse> findAll(Pageable pageable) {
        Page<FamilyOfficeOnboarding> page =
                familyOfficeOnboardingRepository.findByDeletedFalse(resolvePageable(pageable));
        return responseUtil.success(FETCH_ALL_MESSAGE, pageResponse(page));
    }

    public ResponseEntity<ApiResponse> findById(String id) {
        return familyOfficeOnboardingRepository.findByIdAndDeletedFalse(id)
                .map(entity -> responseUtil.success(FETCH_SINGLE_MESSAGE, toResponse(entity)))
                .orElseGet(() -> responseUtil.notFound(NOT_FOUND_MESSAGE, null));
    }

    @Transactional
    public ResponseEntity<ApiResponse> update(String id, FamilyOfficeOnboardingRequest request) {
        log.info("Updating family office onboarding | id={}", id);
        FamilyOfficeOnboarding entity = familyOfficeOnboardingRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (entity == null) {
            log.warn("Family office onboarding update failed: not found | id={}", id);
            return responseUtil.notFound(NOT_FOUND_MESSAGE, null);
        }

        applyRequest(entity, request);
        FamilyOfficeOnboarding saved = familyOfficeOnboardingRepository.save(entity);
        log.info("Family office onboarding updated | id={}", id);
        return responseUtil.success(UPDATE_MESSAGE, toResponse(saved));
    }

    @Transactional
    public ResponseEntity<ApiResponse> delete(String id) {
        log.info("Deleting family office onboarding | id={}", id);
        FamilyOfficeOnboarding entity = familyOfficeOnboardingRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (entity == null) {
            log.warn("Family office onboarding delete failed: not found | id={}", id);
            return responseUtil.notFound(NOT_FOUND_MESSAGE, null);
        }

        entity.setDeleted(true);
        familyOfficeOnboardingRepository.save(entity);
        log.info("Family office onboarding soft-deleted | id={}", id);
        return responseUtil.success(DELETE_MESSAGE, null);
    }

    public ResponseEntity<ApiResponse> search(
            String name,
            String email,
            String country,
            String city,
            String incomeRange,
            MaritalStatus maritalStatus,
            Pageable pageable
    ) {
        Page<FamilyOfficeOnboarding> page = familyOfficeOnboardingRepository.findAll(
                FamilyOfficeOnboardingSpecification.withFilters(name, email, country, city, incomeRange, maritalStatus),
                resolvePageable(pageable)
        );
        return responseUtil.success(FETCH_ALL_MESSAGE, pageResponse(page));
    }

    private void applyRequest(FamilyOfficeOnboarding entity, FamilyOfficeOnboardingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Family office onboarding request is required");
        }

        PersonalInformationRequest personalInformation = request.getPersonalInformation();

        CompletableFuture<String> personalInfoJson    = CompletableFuture.supplyAsync(() -> toJson(personalInformation));
        CompletableFuture<String> familyInfoJson      = CompletableFuture.supplyAsync(() -> toJson(request.getFamilyInformation()));
        CompletableFuture<String> wealthJourneyJson   = CompletableFuture.supplyAsync(() -> toJson(request.getWealthJourney()));
        CompletableFuture<String> financialWorldJson  = CompletableFuture.supplyAsync(() -> toJson(request.getFinancialWorld()));
        CompletableFuture<String> relationshipsJson   = CompletableFuture.supplyAsync(() -> toJson(request.getImportantRelationships()));
        CompletableFuture<String> futureProtectionJson = CompletableFuture.supplyAsync(() -> toJson(request.getFutureProtection()));
        CompletableFuture<String> documentCheckJson   = CompletableFuture.supplyAsync(() -> toJson(request.getDocumentCheck()));
        CompletableFuture<String> priorityAreasJson   = CompletableFuture.supplyAsync(() -> toJson(request.getPriorityAreas()));

        try {
            CompletableFuture.allOf(
                    personalInfoJson, familyInfoJson, wealthJourneyJson, financialWorldJson,
                    relationshipsJson, futureProtectionJson, documentCheckJson, priorityAreasJson
            ).join();

            entity.setPersonalInformation(personalInfoJson.get());
            entity.setFamilyInformation(familyInfoJson.get());
            entity.setWealthJourney(wealthJourneyJson.get());
            entity.setFinancialWorld(financialWorldJson.get());
            entity.setImportantRelationships(relationshipsJson.get());
            entity.setFutureProtection(futureProtectionJson.get());
            entity.setDocumentCheck(documentCheckJson.get());
            entity.setPriorityAreas(priorityAreasJson.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to process family office onboarding request", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Unable to process family office onboarding request", cause);
        }

        entity.setFinalReflection(normalizeOptionalText(request.getFinalReflection()));
        entity.setSearchName(personalInformation.getName());
        entity.setSearchEmail(personalInformation.getEmail());
        entity.setSearchCountry(personalInformation.getCountry());
        entity.setSearchCity(personalInformation.getCity());
        entity.setSearchIncomeRange(personalInformation.getIncomeRange());
        entity.setSearchMaritalStatus(personalInformation.getMaritalStatus().name());
    }

    private FamilyOfficeOnboardingResponse toResponse(FamilyOfficeOnboarding entity) {
        return new FamilyOfficeOnboardingResponse(
                entity.getId(),
                fromJson(entity.getPersonalInformation(), PersonalInformationResponse.class),
                fromJson(entity.getFamilyInformation(), FamilyInformationResponse.class),
                fromJson(entity.getWealthJourney(), WealthJourneyResponse.class),
                fromJson(entity.getFinancialWorld(), FinancialWorldResponse.class),
                fromJsonList(entity.getImportantRelationships(), new TypeReference<List<ImportantRelationshipResponse>>() {
                }),
                fromJson(entity.getFutureProtection(), FutureProtectionResponse.class),
                fromJson(entity.getDocumentCheck(), DocumentCheckResponse.class),
                fromJsonList(entity.getPriorityAreas(), new TypeReference<List<PriorityArea>>() {
                }),
                entity.getFinalReflection(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Map<String, Object> pageResponse(Page<FamilyOfficeOnboarding> page) {
        Map<String, Object> data = new HashMap<>();
        data.put("content", page.getContent().stream().map(this::toResponse).toList());
        data.put("currentPage", page.getNumber());
        data.put("totalPages", page.getTotalPages());
        data.put("totalItems", page.getTotalElements());
        data.put("pageSize", page.getSize());
        return data;
    }

    private Pageable resolvePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by("createdAt").descending());
        }
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt").descending());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize family office onboarding section", e);
            throw new IllegalStateException("Unable to process family office onboarding request");
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Unable to deserialize family office onboarding section {}", type.getSimpleName(), e);
            return null;
        }
    }

    private <T> List<T> fromJsonList(String json, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<T> items = objectMapper.readValue(json, typeReference);
            return items == null ? List.of() : items;
        } catch (Exception e) {
            log.warn("Unable to deserialize family office onboarding list section", e);
            return List.of();
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
