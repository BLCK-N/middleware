package com.myrtle.middleware.services.questionnaire.report;

import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Deterministic client-facing report. Warm, simple and advisory; never shows
 * compliance status, scores or flags. Always produced - the optional AI step
 * only polishes this HTML and falls back to it on any failure.
 */
@Component
public class ClientReportBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    public String build(String clientName, ClassificationResult result) {
        String name = HtmlUtils.escape(clientName == null || clientName.isBlank() ? "Client" : clientName.trim());
        String personaLabel = HtmlUtils.escape(result.persona().displayLabel());
        String riskLabel = HtmlUtils.escape(result.risk().profile().getDisplayName());
        String liquidityLabel = HtmlUtils.escape(result.liquidity().need().getDisplayName());
        String mindset = HtmlUtils.escape(String.join(", ", result.decisionStyle().mindsetThemes()));
        String experience = HtmlUtils.escape(orDash(result.decisionStyle().decisionStyle()));
        String engagement = HtmlUtils.escape(engagementSummary(result));

        String narrative = ReportPhrases.personaNarrative(result.persona().persona())
                + "</p><p style='margin:0 0 16px 0;'>"
                + ReportPhrases.riskNarrative(result.risk().profile())
                + " " + ReportPhrases.liquidityNarrative(result.liquidity().need());

        return TEMPLATE
                .replace("__NAME__", name)
                .replace("__DATE__", LocalDate.now().format(DATE_FORMAT))
                .replace("__PERSONA__", personaLabel)
                .replace("__RISK_PROFILE__", riskLabel)
                .replace("__LIQUIDITY__", liquidityLabel)
                .replace("__MINDSET__", mindset.isBlank() ? "-" : mindset)
                .replace("__EXPERIENCE__", experience)
                .replace("__ENGAGEMENT__", engagement)
                .replace("__NARRATIVE__", narrative)
                .replace("__PERSONA_BASIS__", HtmlUtils.escape(ReportPhrases.personaBasis(result.persona().persona())))
                .replace("__RISK_BASIS__", HtmlUtils.escape(ReportPhrases.riskBasis(result.risk().profile())))
                .replace("__LIQUIDITY_BASIS__", HtmlUtils.escape(ReportPhrases.liquidityBasis(result.liquidity().need())))
                .replace("__DECISION_BASIS__", HtmlUtils.escape(ReportPhrases.DECISION_STYLE_BASIS))
                .replace("__ENGAGEMENT_BASIS__", HtmlUtils.escape(ReportPhrases.ENGAGEMENT_BASIS))
                .replace("__NEXT_STEP__", HtmlUtils.escape(ReportPhrases.recommendedNextStep(result.persona().persona())))
                .replace("__DISCLAIMER__", ReportPhrases.COMPLIANCE_DISCLAIMER)
                .replace("__SIGN_OFF__", ReportPhrases.SIGN_OFF);
    }

    private String engagementSummary(ClassificationResult result) {
        String decision = result.decisionStyle().decisionStyle();
        String communication = result.decisionStyle().communicationPreference();
        if (decision == null && communication == null) {
            return "-";
        }
        if (decision == null) {
            return communication;
        }
        if (communication == null) {
            return decision;
        }
        return decision + " with " + communication.toLowerCase() + " updates";
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static final String TEMPLATE = """
            <div style="font-family:'Mulish',sans-serif;color:#5a6b5a;padding:48px 40px;font-size:15px;line-height:1.7;">
              <p style="font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:2px;color:#42995a;margin:0 0 8px 0;">
                Myrtle Client Discovery &amp; Suitability Summary
              </p>
              <h1 style="font-size:28px;line-height:1.25;color:#1a2e1a;margin:0 0 24px 0;">
                Prepared for <span style="color:#42995a;">__NAME__</span>
              </h1>

              <table style="width:100%;border-collapse:collapse;margin:0 0 28px 0;font-size:14px;">
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;width:40%;">Date Completed</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;">__DATE__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Report Status</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;">Ready for advisor discussion</td>
                </tr>
              </table>

              <h2 style="font-size:18px;color:#1a2e1a;margin:0 0 12px 0;">A Note from Myrtle</h2>
              <p style="margin:0 0 16px 0;">Dear __NAME__,</p>
              <p style="margin:0 0 16px 0;">
                Thank you for completing the Myrtle Client Discovery Questionnaire. What follows is not an audit
                or a final recommendation - it is a starting point for a conversation with your advisor, built
                directly from what you told us.
              </p>

              <h2 style="font-size:18px;color:#1a2e1a;margin:32px 0 12px 0;">Wealth Journey at a Glance</h2>
              <table style="width:100%;border-collapse:collapse;margin:0 0 28px 0;font-size:14px;">
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;width:40%;">Wealth Persona</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;">__PERSONA__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Money Mindset</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;">__MINDSET__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Risk Profile</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;">__RISK_PROFILE__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Liquidity Preference</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;">__LIQUIDITY__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Decision Style</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;">__EXPERIENCE__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Engagement Style</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;">__ENGAGEMENT__</td>
                </tr>
              </table>

              <h2 style="font-size:18px;color:#1a2e1a;margin:32px 0 12px 0;">What Your Responses Suggest</h2>
              <p style="margin:0 0 16px 0;">__NARRATIVE__</p>
              <table style="width:100%;border-collapse:collapse;margin:0 0 28px 0;font-size:13px;">
                <tr>
                  <th style="text-align:left;padding:8px 12px;border:1px solid #e8ede8;background:#f2f5f2;color:#1a2e1a;">Classification Area</th>
                  <th style="text-align:left;padding:8px 12px;border:1px solid #e8ede8;background:#f2f5f2;color:#1a2e1a;">Result</th>
                  <th style="text-align:left;padding:8px 12px;border:1px solid #e8ede8;background:#f2f5f2;color:#1a2e1a;">Basis</th>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Wealth Persona</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;font-weight:600;">__PERSONA__</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">__PERSONA_BASIS__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Risk Profile</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;font-weight:600;">__RISK_PROFILE__</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">__RISK_BASIS__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Liquidity Preference</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;font-weight:600;">__LIQUIDITY__</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">__LIQUIDITY_BASIS__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Decision Style</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;font-weight:600;">__EXPERIENCE__</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">__DECISION_BASIS__</td>
                </tr>
                <tr>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">Engagement Style</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#1a2e1a;font-weight:600;">__ENGAGEMENT__</td>
                  <td style="padding:8px 12px;border:1px solid #e8ede8;color:#4a5e4a;">__ENGAGEMENT_BASIS__</td>
                </tr>
              </table>

              <h2 style="font-size:18px;color:#1a2e1a;margin:32px 0 12px 0;">Suggested Planning Framework</h2>
              <p style="margin:0 0 12px 0;">You may benefit from a portfolio conversation that balances:</p>
              <ul style="margin:0 0 28px 0;padding-left:20px;">
                <li style="margin-bottom:8px;"><strong style="color:#1a2e1a;">Liquidity and safety</strong> - so you have access to funds when needed.</li>
                <li style="margin-bottom:8px;"><strong style="color:#1a2e1a;">Income and stability</strong> - so part of your portfolio can generate predictable returns.</li>
                <li style="margin-bottom:8px;"><strong style="color:#1a2e1a;">Growth and diversification</strong> - so your wealth can expand over time.</li>
              </ul>

              <h2 style="font-size:18px;color:#1a2e1a;margin:32px 0 12px 0;">Focus Areas for Advisor Conversation</h2>
              <ul style="margin:0 0 28px 0;padding-left:20px;">
                <li style="margin-bottom:8px;">Confirming that this summary reflects you accurately.</li>
                <li style="margin-bottom:8px;">Your liquidity requirement, and how soon you may need access to funds.</li>
                <li style="margin-bottom:8px;">The investment amount and time horizon you have in mind.</li>
                <li style="margin-bottom:8px;">How your portfolio should balance stability, income and growth.</li>
              </ul>

              <h2 style="font-size:18px;color:#1a2e1a;margin:0 0 12px 0;">Recommended Next Step</h2>
              <p style="margin:0 0 28px 0;"><strong>Recommended next step:</strong> __NEXT_STEP__</p>

              <h2 style="font-size:18px;color:#1a2e1a;margin:0 0 12px 0;">Important Disclaimer</h2>
              <div style="border-left:3px solid #42995a;background:#f2f5f2;padding:16px 20px;margin:0 0 32px 0;font-size:13px;color:#4a5e4a;">
                __DISCLAIMER__
              </div>

              <p style="margin:0;color:#7a8e7a;">__SIGN_OFF__</p>
            </div>
            """;
}
