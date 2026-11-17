package com.myrtle.middleware.services.nextofkin;

import com.myrtle.middleware.dtos.request.NextOfKinRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.NextOfKinResponse;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.NextOfKin;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.NextOfKinRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NextOfKinService {

    private static final int MIN_NEXT_OF_KIN = 1;
    private static final int MAX_NEXT_OF_KIN = 3;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Set<String> ALLOWED_RELATIONSHIPS = Set.of(
            "spouse", "parent", "father", "mother", "sibling", "brother", "sister",
            "child", "son", "daughter", "guardian", "relative", "uncle", "aunt",
            "cousin", "friend", "other"
    );

    private final NextOfKinRepository nextOfKinRepository;
    private final UserRepository userRepository;
    private final ResponseUtil responseUtil;

    @Transactional
    public ResponseEntity<ApiResponse> addNextOfKin(String userEmail, NextOfKinRequest request) {
        log.info("Adding next of kin | email={}", userEmail);
        ValidationResult validation = validateRequest(request);
        if (!validation.valid()) {
            return responseUtil.badRequest(validation.message(), null);
        }

        User user = findUserByEmail(userEmail);
        String userId = user.getId();

        List<NextOfKin> nextOfKinList = nextOfKinRepository.findByUser_Id(userId);

        if (nextOfKinList.size() >= MAX_NEXT_OF_KIN) {
            log.warn("Next of kin limit reached | email={} | count={}", userEmail, nextOfKinList.size());
            return responseUtil.badRequest("Maximum of 3 next of kin allowed per user", null);
        }
        String normalizedPhone = normalizePhone(request.phoneNumber());

        for (NextOfKin kin : nextOfKinList) {
            if (kin.getPhoneNumber().equals(normalizedPhone)) {
                return responseUtil.conflict("Duplicate phone number for this user", null);
            }
        }

        boolean makePrimary = Boolean.TRUE.equals(request.isPrimary()) || nextOfKinList.isEmpty();
        if (makePrimary) {
            unsetExistingPrimary(nextOfKinList);
        }

        NextOfKin entity = buildEntity(user, request, normalizedPhone, makePrimary);
        NextOfKin saved = nextOfKinRepository.save(entity);

        log.info("Next of kin added | email={} | nextOfKinId={} | isPrimary={}", userEmail, saved.getId(), saved.isPrimary());
        return responseUtil.success("Next of kin added successfully", map(saved));
    }

    public ResponseEntity<ApiResponse> getNextOfKins(String userEmail) {
        User user = findUserByEmail(userEmail);
        String userId = user.getId();

        List<NextOfKinResponse> items = nextOfKinRepository.findByUser_Id(userId)
                .stream()
                .map(this::map)
                .toList();

        return responseUtil.success("Next of kin fetched successfully", items);
    }

    @Transactional
    public ResponseEntity<ApiResponse> updateNextOfKin(String nextOfKinId, String userEmail, NextOfKinRequest request) {
        log.info("Updating next of kin | email={} | nextOfKinId={}", userEmail, nextOfKinId);
        ValidationResult validation = validateRequest(request);
        if (!validation.valid()) {
            return responseUtil.badRequest(validation.message(), null);
        }

        User user = findUserByEmail(userEmail);
        List<NextOfKin> nextOfKinList = nextOfKinRepository.findByUser_Id(user.getId());

        String normalizedPhone = normalizePhone(request.phoneNumber());
        NextOfKin existing = new NextOfKin();

        for (NextOfKin kin : nextOfKinList) {
            if (kin.getId().equals(nextOfKinId)) {
                existing = kin;
            }
        }

        if (existing.getId() == null) {
            throw new ResourceNotFound("Next of kin not found");
        }

        if (!existing.getUser().getId().equals(user.getId())) {
            return responseUtil.forbidden("You are not authorized to update this next of kin", null);
        }

        if (nextOfKinRepository.existsByUser_IdAndPhoneNumberAndIdNot(existing.getUser().getId(), normalizedPhone, existing.getId())) {
            return responseUtil.conflict("A next of kin with the same phone number exists", null);
        }

        boolean makePrimary = Boolean.TRUE.equals(request.isPrimary());
        if (makePrimary) {
            unsetExistingPrimary(nextOfKinList);
            existing.setPrimary(true);
        } else {
            long primaryCount = nextOfKinList.stream()
                    .filter(NextOfKin::isPrimary)
                    .count();
            if (!existing.isPrimary() && primaryCount == 0) {
                return responseUtil.badRequest("One primary next of kin is required", null);
            }
            existing.setPrimary(false);
        }

        existing.setFullName(request.fullName().trim());
        existing.setRelationship(normalizeRelationship(request.relationship()));
        existing.setPhoneNumber(normalizedPhone);
        existing.setEmail(normalizeOptionalEmail(request.email()));
        existing.setAddress(normalizeOptionalText(request.address()));

        NextOfKin saved = nextOfKinRepository.save(existing);

        log.info("Next of kin updated | email={} | nextOfKinId={}", userEmail, nextOfKinId);
        return responseUtil.success("Next of kin updated successfully", map(saved));
    }

    @Transactional
    public ResponseEntity<ApiResponse> deleteNextOfKin(String nextOfKinId, String userEmail) {
        log.info("Deleting next of kin | email={} | nextOfKinId={}", userEmail, nextOfKinId);

        User user = findUserByEmail(userEmail);

        NextOfKin existing = nextOfKinRepository.findById(nextOfKinId)
                .orElseThrow(() -> new ResourceNotFound("Next of kin not found"));

        if (!existing.getUser().getId().equals(user.getId())) {
            return responseUtil.badRequest("You are not authorized to delete this next of kin", null);
        }

        List<NextOfKin> nextOfKinList = nextOfKinRepository.findByUser_Id(existing.getUser().getId());
        if (nextOfKinList.size() == MIN_NEXT_OF_KIN) {
            return responseUtil.badRequest("You must have at least one next of Kin", null);
        }

        boolean deletingPrimary = existing.isPrimary();
        String userId = existing.getUser().getId();

        nextOfKinRepository.delete(existing);
        log.info("Next of kin deleted | email={} | nextOfKinId={} | wasPrimary={}", userEmail, nextOfKinId, deletingPrimary);

        if (deletingPrimary) {
            List<NextOfKin> remaining = nextOfKinRepository.findByUser_Id(userId);

            if (!remaining.isEmpty()) {
                NextOfKin promote = remaining.get(0);
                promote.setPrimary(true);
                nextOfKinRepository.save(promote);
            }
        }

        return responseUtil.success("Next of kin deleted successfully", null);
    }

    public void validateSignupNextOfKins(List<NextOfKinRequest> nextOfKins) {
        if (nextOfKins == null || nextOfKins.isEmpty()) {
            return;
        }

        if (nextOfKins.size() > MAX_NEXT_OF_KIN) {
            throw new IllegalArgumentException("Maximum of 3 next of kin allowed");
        }

        Set<String> phoneSet = new HashSet<>();
        int primaryCount = 0;

        for (NextOfKinRequest request : nextOfKins) {
            ValidationResult validation = validateRequest(request);
            if (!validation.valid()) {
                throw new IllegalArgumentException(validation.message());
            }

            String normalizedPhone = normalizePhone(request.phoneNumber());
            if (!phoneSet.add(normalizedPhone)) {
                throw new IllegalArgumentException("Next of kin phone numbers must be unique");
            }

            if (Boolean.TRUE.equals(request.isPrimary())) {
                primaryCount++;
            }
        }

        if (primaryCount > 1) {
            throw new IllegalArgumentException("Only one next of kin can be set as primary");
        }
    }

    public void createNokForUser(User user, List<NextOfKinRequest> nextOfKins) {
        if (nextOfKins == null || nextOfKins.isEmpty()) {
            return;
        }
        boolean hasPrimary = nextOfKins
                .stream()
                .anyMatch(item ->
                        Boolean.TRUE.equals(item.isPrimary()));
        List<NextOfKin> entities = new ArrayList<>();

        for (int index = 0; index < nextOfKins.size(); index++) {
            NextOfKinRequest item = nextOfKins.get(index);
            boolean makePrimary = Boolean.TRUE.equals(item.isPrimary()) || (!hasPrimary && index == 0);
            entities.add(buildEntity(user, item, normalizePhone(item.phoneNumber()), makePrimary));
        }
        nextOfKinRepository.saveAll(entities);
    }

    private NextOfKin buildEntity(User user, NextOfKinRequest request, String normalizedPhone, boolean primary) {
        NextOfKin entity = new NextOfKin();
        entity.setUser(user);
        entity.setFullName(request.fullName().trim());
        entity.setRelationship(normalizeRelationship(request.relationship()));
        entity.setPhoneNumber(normalizedPhone);
        entity.setEmail(normalizeOptionalEmail(request.email()));
        entity.setAddress(normalizeOptionalText(request.address()));
        entity.setPrimary(primary);
        return entity;
    }

    private void unsetExistingPrimary(List<NextOfKin> existing) {
        boolean changed = false;
        for (NextOfKin item : existing) {
            if (item.isPrimary()) {
                item.setPrimary(false);
                changed = true;
            }
        }
        if (changed) {
            nextOfKinRepository.saveAll(existing);
        }
    }

    private ValidationResult validateRequest(NextOfKinRequest request) {
        if (request == null) {
            return new ValidationResult(false, "Next of kin payload is required");
        }

        if (request.fullName() == null || request.fullName().isBlank()) {
            return new ValidationResult(false, "fullName is required");
        }

        if (request.relationship() == null || request.relationship().isBlank()) {
            return new ValidationResult(false, "relationship is required");
        }

        String relationship = normalizeRelationship(request.relationship());
        if (!ALLOWED_RELATIONSHIPS.contains(relationship.toLowerCase(Locale.ROOT))) {
            return new ValidationResult(false, "Invalid relationship provided");
        }

        if (request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            return new ValidationResult(false, "phoneNumber is required");
        }

        try {
            normalizePhone(request.phoneNumber());
        } catch (IllegalArgumentException ex) {
            return new ValidationResult(false, ex.getMessage());
        }

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                return new ValidationResult(false, "Invalid next of kin email");
            }
        }

        return new ValidationResult(true, "OK");
    }

    private String normalizePhone(String phoneNumber) {
        String input = phoneNumber == null ? "" : phoneNumber.trim();

        String normalized = input.startsWith("+")
                ? "+" + input.substring(1).replaceAll("[^0-9]", "")
                : input.replaceAll("[^0-9]", "");

        String digitsOnly = normalized.startsWith("+") ? normalized.substring(1) : normalized;
        if (digitsOnly.length() < 10 || digitsOnly.length() > 15) {
            throw new IllegalArgumentException("Invalid phone number format");
        }
        return normalized;
    }

    private String normalizeRelationship(String relationship) {
        return relationship == null ? null : relationship.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private NextOfKinResponse map(NextOfKin entity) {
        return new NextOfKinResponse(
                entity.getId(),
                entity.getUser().getId(),
                entity.getFullName(),
                entity.getRelationship(),
                entity.getPhoneNumber(),
                entity.getEmail(),
                entity.getAddress(),
                entity.isPrimary(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmailAddress(userEmail)
                .orElseThrow(() -> new ResourceNotFound("User not found"));
    }

    private record ValidationResult(boolean valid, String message) {
    }
}
