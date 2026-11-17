package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.enums.ProofType;
import com.myrtle.middleware.enums.Status;
import com.myrtle.middleware.model.AddHolderDetails;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.AddHolderDetailsRepository;
import com.myrtle.middleware.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final AddHolderDetailsRepository addHolderDetailsRepository;
    private final UserRepository userRepository;

    @Transactional
    public void completeTier2Upgrade(User user, ProofType proofType, List<String> proofUrls, String nin) {
        persistHolderProofs(user, proofType, proofUrls, Status.IDENTITY_VERIFIED, fallbackAddress(user), false);
        user.setNin(nin);
        user.setTier("2");
        userRepository.save(user);
    }

    @Transactional
    public void submitTier3Review(User user, String address, ProofType proofType, List<String> proofUrls) {
        persistHolderProofs(user, proofType, proofUrls, Status.PENDING, address, true);
    }

    void persistHolderProofs(
            User user,
            ProofType proofType,
            List<String> proofUrls,
            Status desiredStatus,
            String address,
            boolean forceReviewStatus
    ) {
        AddHolderDetails details = addHolderDetailsRepository.findByUser(user)
                .orElseGet(AddHolderDetails::new);
        details.setUser(user);
        details.setProof(mergeProofUrls(details.getProof(), proofUrls));

        boolean protectTier3Workflow = details.getStatus() == Status.PENDING
                || details.getStatus() == Status.VERIFIED;
        if (forceReviewStatus || !protectTier3Workflow) {
            details.setStatus(desiredStatus);
            if (proofType != null) {
                details.setProofType(proofType);
            }
        }

        if (forceReviewStatus && StringUtils.hasText(address)) {
            details.setAddress(address.trim());
        } else if (!StringUtils.hasText(details.getAddress())) {
            details.setAddress(StringUtils.hasText(address) ? address.trim() : fallbackAddress(user));
        }

        if (details.getCreatedAt() == null) {
            details.setCreatedAt(LocalDateTime.now());
        }

        addHolderDetailsRepository.save(details);
        log.info(
                "KYC proofs persisted | userId={} | status={} | proofs={}",
                user.getId(),
                details.getStatus(),
                details.getProof() == null ? 0 : details.getProof().size()
        );
    }

    static List<String> mergeProofUrls(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            for (String url : existing) {
                if (StringUtils.hasText(url)) {
                    merged.add(url.trim());
                }
            }
        }
        if (incoming != null) {
            for (String url : incoming) {
                if (StringUtils.hasText(url)) {
                    merged.add(url.trim());
                }
            }
        }
        return new ArrayList<>(merged);
    }

    private static String fallbackAddress(User user) {
        if (user != null && StringUtils.hasText(user.getAddress())) {
            return user.getAddress().trim();
        }
        return "Nigeria";
    }
}
