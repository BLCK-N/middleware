package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.request.Tier3UpgradePayLoad;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.enums.Status;
import com.myrtle.middleware.model.AddHolderDetails;
import com.myrtle.middleware.repository.AddHolderDetailsRepository;
import com.myrtle.middleware.repository.HoldPersonalDetailsRepository;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycResendCron {
    private final JwtUtils jwtUtil;
    private final EmailService emailService;
    private final ResponseUtil responseUtil;
    private final AddHolderDetailsRepository addHolderDetailsRepository;
    private final HoldPersonalDetailsRepository holdPersonalDetailsRepository;

    //    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Africa/Lagos")
    public ResponseEntity<ApiResponse> resendPendingKycRequests() {
        List<AddHolderDetails> holderDetails = addHolderDetailsRepository.findAll();

        if (holderDetails.isEmpty()) {
            return responseUtil.success("No add holder details found", Map.of(
                    "total", 0,
                    "resent", 0,
                    "skipped", 0,
                    "failed", 0
            ));
        }

        int resent = 0;
        int skipped = 0;
        int failed = 0;

        for (AddHolderDetails details : holderDetails) {
            try {
                if (details == null
                        || details.getUser() == null
                        || details.getUser().getEmailAddress() == null
                        || details.getAddress() == null
                        || details.getAddress().isBlank()
                        || details.getProofType() == null
                        || details.getStatus() != Status.PENDING
                ) {
                    skipped++;
                    continue;
                }

                String[] proof = details.getProof() == null
                        ? new String[]{}
                        : details.getProof().toArray(new String[0]);

                Tier3UpgradePayLoad payload = new Tier3UpgradePayLoad(
                        details.getAddress(),
                        proof,
                        details.getProofType()
                );

                String accessToken = jwtUtil.generateToken(details.getUser().getEmailAddress());

                String CUSTOMER_CARE_EMAIL = "wecare@myrtleng.com";
                emailService.sendMailToCustomerCareUpgrade(
                        CUSTOMER_CARE_EMAIL,
                        payload,
                        accessToken,
                        details.getUser()
                );

                resent++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to resend add holder details mail for recordId={}", details.getId(), e);
            }
        }

        return responseUtil.success("Rework sender details completed", Map.of(
                "total", holderDetails.size(),
                "resent", resent,
                "skipped", skipped,
                "failed", failed
        ));
    }
}
