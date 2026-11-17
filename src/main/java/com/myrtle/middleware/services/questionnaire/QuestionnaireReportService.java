package com.myrtle.middleware.services.questionnaire;

import com.myrtle.middleware.enums.ComplianceStatus;
import com.myrtle.middleware.enums.QuestionnaireEmailStatus;
import com.myrtle.middleware.model.QuestionnaireResult;
import com.myrtle.middleware.repository.QuestionnaireResultRepository;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.OpenAiService;
import com.myrtle.middleware.services.questionnaire.report.ReportPhrases;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Post-submission delivery step. AI is used only to polish the narrative of
 * the deterministic client report; any AI or email failure degrades gracefully
 * and never disturbs the stored classification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireReportService {

    private final EmailService emailService;
    private final ExternalAPIs externalAPIs;
    private final OpenAiService openAiService;
    private final QuestionnaireResultRepository questionnaireResultRepository;

    @Value("${active.environment}")
    private String activeEnv;

    @Async("reportExecutor")
    public void polishAndDeliver(String resultId) {
        QuestionnaireResult entity = questionnaireResultRepository.findById(resultId).orElse(null);
        if (entity == null) {
            log.error("Questionnaire result not found for report delivery | id={}", resultId);
            return;
        }

        if (entity.getComplianceStatus() == ComplianceStatus.HOLD_BEFORE_EXECUTION) {
            log.info("Client report email withheld: compliance hold | id={} | email={}",
                    resultId, entity.getClientEmail());
            entity.setEmailStatus(QuestionnaireEmailStatus.WITHHELD_HOLD);
            questionnaireResultRepository.save(entity);
            return;
        }

        polishClientReport(entity);

        try {
            emailService.deliverQuestionnaireReport(entity.getClientEmail(), entity.getClientReportHtml());
            if (!activeEnv.equalsIgnoreCase("dev")) {
                externalAPIs.sendTransactionalEmail("compliance@myrtleng.com", "New Survey submission - " + entity.getClientName(),
                        entity.getInternalReportHtml());
            }
            entity.setEmailStatus(QuestionnaireEmailStatus.SENT);
            entity.setEmailSentAt(LocalDateTime.now());
            log.info("Questionnaire report delivered | id={} | email={} | aiPolished={}",
                    resultId, entity.getClientEmail(), entity.isClientReportAiPolished());
        } catch (Exception e) {
            log.error("Questionnaire report email failed | id={} | email={}",
                    resultId, entity.getClientEmail(), e);
            entity.setEmailStatus(QuestionnaireEmailStatus.FAILED);
        }
        questionnaireResultRepository.save(entity);
    }

    private void polishClientReport(QuestionnaireResult entity) {
        try {
            String polished = openAiService.polishQuestionnaireReportV2(entity.getClientReportHtml());
            if (isAcceptablePolish(polished)) {
                entity.setClientReportHtml(polished);
                entity.setClientReportAiPolished(true);
            } else {
                log.warn("AI polish rejected (validation failed), keeping deterministic report | id={}", entity.getId());
            }
        } catch (Exception e) {
            log.warn("AI polish failed, keeping deterministic report | id={} | reason={}",
                    entity.getId(), e.getMessage());
        }
    }

    /**
     * The polished HTML must still carry the verbatim compliance disclaimer
     * and the verbatim sign-off; anything else means the AI overstepped its
     * narrative-only mandate (e.g. inventing its own closing line).
     */
    private boolean isAcceptablePolish(String polished) {
        return polished != null && !polished.isBlank()
                && polished.contains(ReportPhrases.COMPLIANCE_DISCLAIMER)
                && polished.contains(ReportPhrases.SIGN_OFF);
    }
}
