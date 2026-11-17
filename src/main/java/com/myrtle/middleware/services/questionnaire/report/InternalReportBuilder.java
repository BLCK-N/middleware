package com.myrtle.middleware.services.questionnaire.report;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.enums.ComplianceStatus;
import com.myrtle.middleware.enums.WealthPersona;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.AppliedCap;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.Flag;
import com.myrtle.middleware.services.questionnaire.engine.ResponseSet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Internal advisor and compliance view: scoring breakdowns, flags, review
 * status and execution restrictions. Fully deterministic, never AI-touched
 * and never emailed to the client - persisted for the backoffice only.
 */
@Component
public class InternalReportBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    public String build(String clientName, int submissionVersion, ClassificationResult result,
                        ResponseSet responses, QuestionnaireScoringConfig config) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:'Mulish',sans-serif;color:#333;font-size:14px;line-height:1.6;padding:24px;\">");

        appendHeader(html, clientName, submissionVersion, result, config);
        appendFullResponses(html, responses, config);
        appendClassificationSummary(html, result);
        appendRiskBreakdown(html, result, responses, config);
        appendPersonaBreakdown(html, result);
        appendCapacityIndicators(html, result, responses, config);
        appendComplianceReview(html, result, responses, config);
        appendSuitabilityNotes(html, result);
        appendFlags(html, result);
        appendInternalRecommendation(html, result);

        html.append("<p style='font-size:12px;color:#777;margin-top:24px;'>")
                .append(ReportPhrases.COMPLIANCE_DISCLAIMER)
                .append("</p></div>");
        return html.toString();
    }

    private void appendHeader(StringBuilder html, String clientName, int submissionVersion,
                              ClassificationResult result, QuestionnaireScoringConfig config) {
        html.append("<h1 style='font-size:20px;color:#1a2e1a;margin:0 0 4px 0;'>Internal Advisor &amp; Compliance Review</h1>");
        row(html, "Client Name", HtmlUtils.escape(clientName));
        row(html, "Date Completed", LocalDate.now().format(DATE_FORMAT));
        row(html, "Submission Version", "V" + submissionVersion);
        row(html, "Scoring Config Version", HtmlUtils.escape(config.configVersion()));
        row(html, "Internal Status", internalStatusLine(result.compliance().status()));
        row(html, "Cleared for Advisory Discussion",
                result.compliance().status() == ComplianceStatus.HOLD_BEFORE_EXECUTION
                        ? "<strong style='color:#b23b3b;'>No - Hold Before Execution</strong>"
                        : "Yes");
        html.append("<hr style='border:none;border-top:1px solid #ddd;margin:16px 0;'/>");
    }

    /**
     * Every question and the client's raw answer, in questionnaire order -
     * the full record an advisor or compliance reviewer can audit against
     * what the client actually submitted.
     */
    private void appendFullResponses(StringBuilder html, ResponseSet responses, QuestionnaireScoringConfig config) {
        html.append(sectionTitle("Full Questionnaire Responses"));
        html.append(tableOpen("Question", "Response"));
        for (QuestionnaireScoringConfig.QuestionDef question : config.questions()) {
            String code = question.code();
            List<String> selected = responses.optionsOf(code);
            String selectedLabels = selected.stream()
                    .map(option -> config.optionLabel(code, option))
                    .collect(Collectors.joining("; "));
            String freeText = responses.freeText(code).orElse(null);

            String responseText;
            if (!selectedLabels.isBlank() && freeText != null && !freeText.isBlank()) {
                responseText = selectedLabels + " (" + freeText + ")";
            } else if (!selectedLabels.isBlank()) {
                responseText = selectedLabels;
            } else if (freeText != null && !freeText.isBlank()) {
                responseText = freeText;
            } else {
                responseText = "Not answered";
            }

            html.append("<tr>")
                    .append(cell("<strong>" + HtmlUtils.escape(code) + "</strong> - " + HtmlUtils.escape(question.label())))
                    .append(cell(HtmlUtils.escape(responseText)))
                    .append("</tr>");
        }
        html.append("</table>");
    }

    private void appendClassificationSummary(StringBuilder html, ClassificationResult result) {
        html.append(sectionTitle("Classification Summary"));
        html.append(tableOpen("Dimension", "Result"));
        tableRow(html, "Wealth Persona", HtmlUtils.escape(result.persona().displayLabel()));
        tableRow(html, "Risk Profile", HtmlUtils.escape(result.risk().profile().getDisplayName()));
        tableRow(html, "Liquidity Need", HtmlUtils.escape(result.liquidity().need().getDisplayName()));
        tableRow(html, "Financial Capacity", HtmlUtils.escape(result.capacity().category().getDisplayName()));
        tableRow(html, "Decision Style", HtmlUtils.escape(orDash(result.decisionStyle().decisionStyle())));
        tableRow(html, "Communication Preference", HtmlUtils.escape(orDash(result.decisionStyle().communicationPreference())));
        tableRow(html, "Privacy / Openness", HtmlUtils.escape(orDash(result.decisionStyle().privacyOpenness())));
        tableRow(html, "Compliance Status", HtmlUtils.escape(result.compliance().status().getDisplayName()));
        tableRow(html, "Suitability Flag", result.risk().suitabilityMismatch()
                ? "<strong style='color:#b23b3b;'>Suitability mismatch identified</strong>"
                : "No major mismatch identified");
        html.append("</table>");
    }

    private void appendRiskBreakdown(StringBuilder html, ClassificationResult result,
                                     ResponseSet responses, QuestionnaireScoringConfig config) {
        html.append(sectionTitle("Risk Scoring Breakdown"));
        html.append(tableOpen("Risk Area", "Client Response", "Score", "Weight"));
        Map<String, String> areaNames = config.riskScoring().areaNames();
        for (Map.Entry<String, Integer> entry : result.risk().perQuestionScores().entrySet()) {
            String question = entry.getKey();
            String responseLabel = responses.singleOption(question)
                    .map(option -> config.optionLabel(question, option))
                    .orElse("-");
            Double weight = config.riskScoring().weights().get(question);
            html.append("<tr>")
                    .append(cell(HtmlUtils.escape(areaNames.getOrDefault(question, question))))
                    .append(cell(HtmlUtils.escape(responseLabel)))
                    .append(cell(String.valueOf(entry.getValue())))
                    .append(cell(weight == null ? "-" : Math.round(weight * 100) + "%"))
                    .append("</tr>");
        }
        html.append("</table>");
        html.append("<p><strong>Weighted Risk Score:</strong> ").append(result.risk().weightedScore())
                .append(" &rarr; ").append(HtmlUtils.escape(result.risk().preOverrideProfile().getDisplayName()));
        if (result.risk().profile() != result.risk().preOverrideProfile()) {
            html.append(" (capped to <strong>")
                    .append(HtmlUtils.escape(result.risk().profile().getDisplayName()))
                    .append("</strong>)");
        }
        html.append("</p>");
        for (AppliedCap cap : result.risk().appliedCaps()) {
            html.append("<p style='color:#b23b3b;margin:4px 0;'>Override applied: ")
                    .append(HtmlUtils.escape(cap.reason()))
                    .append(" (").append(HtmlUtils.escape(cap.trigger())).append(") capped ")
                    .append(HtmlUtils.escape(cap.from().getDisplayName()))
                    .append(" &rarr; ")
                    .append(HtmlUtils.escape(cap.to().getDisplayName()));
            if (cap.advisorOverridable()) {
                html.append(" - advisor may override after discussion");
            }
            html.append("</p>");
        }
    }

    private void appendPersonaBreakdown(StringBuilder html, ClassificationResult result) {
        html.append(sectionTitle("Persona Scoring Breakdown"));
        html.append(tableOpen("Persona", "Score"));
        for (Map.Entry<WealthPersona, Integer> entry : result.persona().scores().entrySet()) {
            tableRow(html, HtmlUtils.escape(entry.getKey().getDisplayName()), String.valueOf(entry.getValue()));
        }
        html.append("</table>");
        html.append("<p><strong>Persona Result:</strong> ")
                .append(HtmlUtils.escape(result.persona().displayLabel())).append("</p>");
        for (String reason : result.persona().reasons()) {
            html.append("<p style='margin:4px 0;color:#555;'>").append(HtmlUtils.escape(reason)).append("</p>");
        }
    }

    private void appendCapacityIndicators(StringBuilder html, ClassificationResult result,
                                          ResponseSet responses, QuestionnaireScoringConfig config) {
        html.append(sectionTitle("Financial Capacity &amp; Net Worth Indicators"));
        html.append(tableOpen("Area", "Client Response", "Score"));
        Map<String, String> areaLabels = Map.of(
                "Q11", "Annual Income",
                "Q13", "Liquid Assets",
                "Q14", "Real Estate / Land",
                "Q15", "Business / Income-Generating Assets",
                "Q16", "Debt Obligations");
        for (Map.Entry<String, Double> entry : result.capacity().perQuestionScores().entrySet()) {
            String question = entry.getKey();
            String responseLabel = responses.singleOption(question)
                    .map(option -> config.optionLabel(question, option))
                    .orElse("-");
            html.append("<tr>")
                    .append(cell(HtmlUtils.escape(areaLabels.getOrDefault(question, question))))
                    .append(cell(HtmlUtils.escape(responseLabel)))
                    .append(cell(formatScore(entry.getValue())))
                    .append("</tr>");
        }
        html.append("</table>");
        html.append("<p><strong>Indicative Capacity:</strong> ")
                .append(HtmlUtils.escape(result.capacity().category().getDisplayName()))
                .append(" (total score ").append(formatScore(result.capacity().totalScore())).append(")</p>");
        html.append("<p style='font-size:12px;color:#777;'>This is based on client-declared information and "
                + "must be verified where required through onboarding, KYC and supporting documentation.</p>");
    }

    private void appendComplianceReview(StringBuilder html, ClassificationResult result,
                                        ResponseSet responses, QuestionnaireScoringConfig config) {
        html.append(sectionTitle("Compliance Review"));
        String sourceOfFunds = responses.optionsOf("Q21").stream()
                .map(option -> config.optionLabel("Q21", option))
                .collect(Collectors.joining(", "));
        String ubo = responses.singleOption("Q22")
                .map(option -> config.optionLabel("Q22", option))
                .orElse("-");
        html.append(tableOpen("Compliance Area", "Status"));
        tableRow(html, "Source of Funds", HtmlUtils.escape(sourceOfFunds.isBlank() ? "-" : sourceOfFunds));
        responses.freeText("Q21").ifPresent(text ->
                tableRow(html, "Source of Funds - Details", HtmlUtils.escape(text)));
        tableRow(html, "Ultimate Beneficial Owner", HtmlUtils.escape(ubo));
        responses.freeText("Q22").ifPresent(text ->
                tableRow(html, "UBO - Details", HtmlUtils.escape(text)));
        tableRow(html, "Missing Information", result.compliance().missingInformation().isEmpty()
                ? "None indicated"
                : HtmlUtils.escape(String.join("; ", result.compliance().missingInformation())));
        tableRow(html, "Enhanced Due Diligence Required",
                result.compliance().status() == ComplianceStatus.ENHANCED_REVIEW ? "Yes" : "No, based on questionnaire response");
        tableRow(html, "Hold Before Execution",
                result.compliance().status() == ComplianceStatus.HOLD_BEFORE_EXECUTION ? "<strong style='color:#b23b3b;'>Yes</strong>" : "No");
        tableRow(html, "Documents Required", "Standard KYC, proof of identity, proof of address, "
                + "source-of-funds support where applicable");
        html.append("</table>");
    }

    private void appendSuitabilityNotes(StringBuilder html, ClassificationResult result) {
        html.append(sectionTitle("Suitability Notes"));
        StringBuilder notes = new StringBuilder();
        notes.append("The client appears suitable for a ")
                .append(result.risk().profile().getDisplayName().toLowerCase())
                .append(" investment conversation. ");
        switch (result.liquidity().need()) {
            case HIGH -> notes.append("The client has a very high liquidity need; long-tenor or illiquid "
                    + "investments should not be recommended without a deeper discussion.");
            case MODERATE -> notes.append("The client has a moderate liquidity need. Long-tenor or illiquid "
                    + "investments should not be recommended unless the advisor confirms that only a portion "
                    + "of the portfolio will be allocated and the client understands the liquidity restriction.");
            case LOW, VERY_LOW -> notes.append("The client's liquidity need is low, allowing longer-tenor "
                    + "options to be considered where suitable.");
        }
        if (result.risk().suitabilityMismatch()) {
            notes.append(" A suitability mismatch was identified between the client's growth goal and their "
                    + "loss tolerance / liquidity need - this must be resolved in the advisory conversation.");
        }
        notes.append(" Product-fit against any specific intended investment remains an advisor check and is "
                + "not assessed by the questionnaire.");
        html.append("<p>").append(notes).append("</p>");
        for (Flag flag : result.liquidity().flags()) {
            html.append("<p style='margin:4px 0;color:#8a6d1a;'>Liquidity flag: ")
                    .append(HtmlUtils.escape(flag.description())).append("</p>");
        }
    }

    private void appendFlags(StringBuilder html, ClassificationResult result) {
        html.append(sectionTitle("Compliance Flags"));
        if (result.compliance().flags().isEmpty()) {
            html.append("<p>No major red flag from questionnaire responses.</p>");
        } else {
            html.append("<ul style='margin:0 0 12px 0;padding-left:20px;'>");
            for (Flag flag : result.compliance().flags()) {
                html.append("<li style='margin-bottom:6px;'><strong>")
                        .append(HtmlUtils.escape(flag.code()))
                        .append(":</strong> ")
                        .append(HtmlUtils.escape(flag.description()))
                        .append("</li>");
            }
            html.append("</ul>");
        }
        html.append("<p>Before execution, the following must be confirmed: KYC completion; source-of-funds "
                + "documentation; beneficial ownership confirmation; investment amount; liquidity requirement; "
                + "final suitability approval.</p>");
    }

    private void appendInternalRecommendation(StringBuilder html, ClassificationResult result) {
        html.append(sectionTitle("Internal Recommendation"));
        switch (result.compliance().status()) {
            case STANDARD_REVIEW -> html.append("<p>Proceed to advisor suitability discussion. Do not execute "
                    + "investment until KYC is completed, source of funds is reviewed, the client confirms the "
                    + "investment objective, the advisor confirms suitability and the compliance review is "
                    + "completed.</p><p><strong>Internal Status:</strong> Cleared for advisory discussion, "
                    + "not yet cleared for execution.</p>");
            case ENHANCED_REVIEW -> html.append("<p>Proceed to advisor suitability discussion alongside "
                    + "enhanced due diligence on the flagged items above. Execution must not proceed until "
                    + "enhanced review is completed and documented.</p><p><strong>Internal Status:</strong> "
                    + "Enhanced review required before execution.</p>");
            case HOLD_BEFORE_EXECUTION -> html.append("<p>Do not proceed. Obtain the missing information "
                    + "listed above and route to compliance before any advisory execution step. The client "
                    + "report email has been withheld.</p><p><strong>Internal Status:</strong> Hold before "
                    + "execution.</p>");
        }
    }

    private String internalStatusLine(ComplianceStatus status) {
        return switch (status) {
            case STANDARD_REVIEW -> "Standard Review Required Before Execution";
            case ENHANCED_REVIEW -> "Enhanced Review Required Before Execution";
            case HOLD_BEFORE_EXECUTION -> "Hold Before Execution";
        };
    }

    private void row(StringBuilder html, String label, String value) {
        html.append("<p style='margin:2px 0;'><strong>").append(label).append(":</strong> ")
                .append(value).append("</p>");
    }

    private String sectionTitle(String title) {
        return "<h2 style='font-size:16px;color:#1a2e1a;margin:20px 0 8px 0;'>" + title + "</h2>";
    }

    private String tableOpen(String... headers) {
        StringBuilder sb = new StringBuilder("<table style='width:100%;border-collapse:collapse;font-size:13px;'><tr>");
        for (String header : headers) {
            sb.append("<th style='text-align:left;padding:6px 10px;border:1px solid #ddd;background:#f2f5f2;color:#1a2e1a;'>")
                    .append(header).append("</th>");
        }
        return sb.append("</tr>").toString();
    }

    private void tableRow(StringBuilder html, String label, String value) {
        html.append("<tr>").append(cell(label)).append(cell(value)).append("</tr>");
    }

    private String cell(String value) {
        return "<td style='padding:6px 10px;border:1px solid #ddd;'>" + value + "</td>";
    }

    private String formatScore(double score) {
        return score == Math.floor(score) ? String.valueOf((int) score) : String.valueOf(score);
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
