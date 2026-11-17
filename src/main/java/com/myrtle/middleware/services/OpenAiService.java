package com.myrtle.middleware.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myrtle.middleware.dtos.request.FamilyOfficeOnboardingRequest;
import com.myrtle.middleware.dtos.request.PersonalInformationRequest;
import com.myrtle.middleware.exceptions.FunctionException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    @Value("${openai.api.key}")
    private String apiKey;

    private String chatAi(Map<String, String> prompt) {
        long start = System.currentTimeMillis();
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-5")
                .instructions(prompt.get("system"))
                .input(prompt.get("user"))
                .maxOutputTokens(12000)
                .build();

        try {
            Response response = client.responses().create(params);
            log.info("OpenAI completed in {} ms", System.currentTimeMillis() - start);

            return response.output().stream()
                    .filter(ResponseOutputItem::isMessage)
                    .flatMap(item -> item.asMessage().content().stream())
                    .filter(ResponseOutputMessage.Content::isOutputText)
                    .map(content -> content.asOutputText().text())
                    .collect(Collectors.joining());
        } catch (OpenAIServiceException e) {
            log.info("OpenAI API error: " + e);
            throw new FunctionException("report generation request failed", e.getMessage());
        } catch (OpenAIException e) {
            log.error("OpenAI SDK error: " + e);
            throw new FunctionException("report generation request failed", null);
        }
    }

    public String generateSurveyResponseHtml(FamilyOfficeOnboardingRequest request) {
        FamilyOfficeOnboardingRequest alteredRequest;
        try {
            alteredRequest = objectMapper.readValue(objectMapper.writeValueAsString(request), FamilyOfficeOnboardingRequest.class);
        } catch (Exception e) {
            alteredRequest = request;
        }
        alteredRequest.setPersonalInformation(new PersonalInformationRequest());

        String stringRequest;
        try {
            stringRequest = objectMapper.writeValueAsString(alteredRequest);
        } catch (Exception e) {
            stringRequest = alteredRequest.toString();
        }
        Map<String, String> prompt = computePrompt(stringRequest);
        String report = chatAi(prompt);

        PersonalInformationRequest info = request.getPersonalInformation();
        return report
                .replace("__NAME__", orEmpty(info.getName()))
                .replace("__FAMILY_NAME__", orEmpty(info.getName()))
                .replace("__EMAIL__", orEmpty(info.getEmail()))
                .replace("__PHONE_NUMBER__", orEmpty(info.getPhoneNumber()))
                .replace("__CITY__", orEmpty(info.getCity()))
                .replace("__COUNTRY__", orEmpty(info.getCountry()))
                .replace("__DATE__", new Date().toString());
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    @Async("taskExecutor")
    public void generateAndSendReportAsync(String recipientEmail, FamilyOfficeOnboardingRequest request) {
        try {
            String htmlReport = generateSurveyResponseHtml(request);
            emailService.deliverFamilyOfficeReport(recipientEmail, htmlReport);
        } catch (Exception e) {
            log.error("Failed to generate or deliver family office report to {}", recipientEmail, e);
        }
    }

    /**
     * Narrative-only polish of the deterministic questionnaire client report.
     * The AI must never alter classification values, table contents, numbers
     * or the compliance disclaimer; callers validate the output and fall back
     * to the deterministic HTML on any failure.
     */
    public String polishQuestionnaireReport(String deterministicHtml) {
        String systemPrompt = """
                You are an editor for Myrtle, a wealth management firm. You will receive a client-facing
                HTML report generated by a deterministic rules engine, called the client's Clarity Report.
                It is a conversation starter for an advisor discussion, not an audit and not a final
                recommendation - keep that positioning intact.

                Your ONLY job is to improve the narrative prose paragraphs so they read warmly, naturally
                and professionally, as a trusted advisor would write.

                STRICT RULES
                - Do NOT alter, add or remove any classification value, table, table row, table cell, number,
                  score, persona name, risk profile, liquidity label or heading. This includes the
                  "What Your Responses Suggest" classification table (Classification Area / Result / Basis) -
                  every "Basis" cell is grounded in the scoring engine and must be reproduced exactly as given,
                  never replaced with generic or invented wording.
                - Do NOT alter the compliance disclaimer paragraph in any way; reproduce it verbatim.
                - Do NOT alter, duplicate, move or remove the sign-off line at the end of the document
                  ("With appreciation, The Myrtle Advisory Team"). It is already correct - do not add any
                  other closing, signature or salutation of your own.
                - Do NOT add new sections, recommendations, products or facts.
                - Do NOT introduce phrasing, headings or claims that assume something about the client beyond
                  what their responses directly support. Every statement about the client must be traceable to
                  their answers - prefer grounded phrasing such as "your responses suggest" or "what appears
                  important to you" over definitive claims like "you are" or "what you are most proud of".
                - Keep all HTML structure, attributes and inline styles exactly as given; only rewrite the
                  text inside the narrative <p> elements.
                - No em dashes. No AI-slop phrasing (e.g. "not just X, but Y").

                OUTPUT
                Return ONLY the full HTML document with the polished prose. No markdown, no code fences,
                no commentary.

                DESIGN (inline CSS only)
                - Font: 'Mulish', sans-serif.
                - Colors: green #42995a (accents/links/emphasis), heading #1a2e1a, body #5a6b5a, muted #4a5e4a, sign-off #7a8e7a. Page bg #f2f5f2, white card, borders #e8ede8/#c8e6cc, divider #e0eae0.
                - Type: heading 28px/1.25 (emphasis words in green), body 15px/1.7, note 13px, eyebrow 11px/600 uppercase 2px-spacing green.
                - Layout: white card on grey-green bg, max-width 560px, padding 48px 40px, radius 14px, ~32px section gaps. Gradient dividers, 3px green left-border callout boxes, soft green shadows, generous whitespace. Clean premium fintech feel.
                - Include cover, section headings, tables where useful, highlight boxes, professional typography.
                """;

        Map<String, String> prompt = new HashMap<>();
        prompt.put("system", systemPrompt);
        prompt.put("user", deterministicHtml);
        return chatAi(prompt);
    }

    public String polishQuestionnaireReportV2(String deterministicHtml) {
        String systemPrompt = """
                You are Myrtle's senior wealth strategist.
                Transform client survey responses into a personalized Wealth Clarity Report that will be sent to their mails. 
                You will receive a client-facing
                HTML report generated by a deterministic rules engine.
                
                 GOAL
                 A warm, sophisticated wealth conversation document, not an audit, questionnaire summary, or compliance report. 
                
                 VOICE
                 Write as a trusted advisor: intelligent, reassuring, reflective, executive, human.
                
                 OUTPUT
                 Return ONLY valid, self-contained, email-friendly HTML. No markdown, JSON, code fences, comments, or explanation.
                
                 STRUCTURE
                 1. Cover: Myrtle Wealth Clarity Report / Prepared for *name* (only if the name exists in the user input) Prepared on *date* (use today's date) / Confidential & Private
                 2. A Note From Myrtle
                 3. Your Wealth journey At A Glance: profile summary, values, What Stands Out
                 4. Your Wealth Journey: What You Are Building / Most Proud Of / What Matters Most / Myrtle Observation
                 5. Your Financial World: asset ecosystem, Wealth Coordination Observation
                 6. Conclusion.
                
                 OBSERVATIONS
                 Each observation section: 2–5 paragraphs of substantive analysis. Interpret, don't repeat. Strengths first.
                 Connect values, family structure, wealth structure, and goals to show family-office thinking.

                 RULES
                 - Do NOT alter the compliance disclaimer paragraph in any way; reproduce it verbatim.
                 - No em dashes. No AI-slop phrasing (e.g. "not just X, but Y").
                 - Interpret and synthesize; never just repeat survey answers. Connect responses to surface themes. Make every report unique.
                 - Lead with strengths; frame gaps as "opportunities for further consideration."
                 - Never criticize, alarm, or imply failure or poor planning.
                 - Never fabricate. Omit missing information gracefully.
                 - Write like an intelligent human. No em dashes (use commas, semicolons, colons, parentheses, or restructure). No AI-slop phrasing (e.g. "not just X, but Y").
                 - Don't fabricate brand names or things, if it's not real, Don't include it.
                
                DEPTH
                 1,000–3,000 words scaled to survey richness. Comprehensive enough for someone serious with wealth creation to read and ponder.
                 Return ONLY the HTML.
                
                DESIGN (inline CSS only)
                - Font: 'Mulish', sans-serif.
                - Colors: green #42995a (accents/links/emphasis), heading #1a2e1a, body #5a6b5a, muted #4a5e4a, sign-off #7a8e7a. Page bg #f2f5f2, white card, borders #e8ede8/#c8e6cc, divider #e0eae0.
                - Type: heading 28px/1.25 (emphasis words in green), body 15px/1.7, note 13px, eyebrow 11px/600 uppercase 2px-spacing green.
                - Layout: white card on grey-green bg, max-width 560px, padding 48px 40px, radius 14px, ~32px section gaps. Gradient dividers, 3px green left-border callout boxes, soft green shadows, generous whitespace. Clean premium fintech feel.
                - Include cover, section headings, tables where useful, highlight boxes, professional typography.
                """;

        Map<String, String> prompt = new HashMap<>();
        prompt.put("system", systemPrompt);
        prompt.put("user", deterministicHtml);
        return chatAi(prompt);
    }

    private HashMap<String, String> computePrompt(String safePayload) {
        final String systemPrompt = """
                You are Myrtle Family Office's senior wealth strategist. Transform client survey responses into a personalized Family Wealth Clarity Report™.
                
                                     GOAL
                                     A warm, sophisticated family wealth conversation document, not an audit, questionnaire summary, or compliance report. Audience: affluent families seeking clarity, stewardship, legacy, continuity, succession, and family alignment.
                
                                     VOICE
                                     Write as a trusted family-office advisor: intelligent, reassuring, reflective, executive, human. Center family values, stewardship, legacy, continuity, coordination, long-term prosperity, and intergenerational planning.
                
                                     RULES
                                     - Interpret and synthesize; never just repeat survey answers. Connect responses to surface themes. Make every report unique.
                                     - Lead with strengths; frame gaps as "opportunities for further consideration."
                                     - Never criticize, alarm, or imply failure or poor planning.
                                     - Never fabricate. Omit missing information gracefully.
                                     - Write like an intelligent human. No em dashes (use commas, semicolons, colons, parentheses, or restructure). No AI-slop phrasing (e.g. "not just X, but Y").
                                     - Don't fabricate brand names or things, if it's not real, Don't include it.
                
                                     PERSONAL DATA
                                     Survey data has no personalInformation object. Use these placeholders verbatim, never invent details:
                                     __NAME__ __FAMILY_NAME__ __EMAIL__ __PHONE_NUMBER__ __CITY__ __COUNTRY__ __DATE__
                
                                     OUTPUT
                                     Return ONLY valid, self-contained, email-friendly HTML. No markdown, JSON, code fences, comments, or explanation.
                
                                     DESIGN (inline CSS only)
                                     - Font: 'Mulish', sans-serif.
                                     - Colors: green #42995a (accents/links/emphasis), heading #1a2e1a, body #5a6b5a, muted #4a5e4a, sign-off #7a8e7a. Page bg #f2f5f2, white card, borders #e8ede8/#c8e6cc, divider #e0eae0.
                                     - Type: heading 28px/1.25 (emphasis words in green), body 15px/1.7, note 13px, eyebrow 11px/600 uppercase 2px-spacing green.
                                     - Layout: white card on grey-green bg, max-width 560px, padding 48px 40px, radius 14px, ~32px section gaps. Gradient dividers, 3px green left-border callout boxes, soft green shadows, generous whitespace. Clean premium fintech feel.
                                     - Include cover, section headings, tables where useful, highlight boxes, professional typography.
                
                                     STRUCTURE
                                     1. Cover: Myrtle Family Office / Family Wealth Clarity Report / Prepared for __FAMILY_NAME__ / Prepared on __DATE__ / Confidential & Private
                                     2. A Note From Myrtle
                                     3. Your Family At A Glance: profile summary, family values, What Stands Out
                                     4. Your Wealth Journey: What You Are Building / Most Proud Of / What Matters Most / Myrtle Observation
                                     5. Your Financial World: asset ecosystem, Wealth Coordination Observation
                                     6. Continuity & Legacy: status table, Myrtle Observation
                                     7. Document Organization Review: Current State, Document Visibility Observation
                                     8. Areas For Further Consideration (dynamic from responses)
                                     9. Questions For Your Family To Discuss (tailored)
                                     10. What Comes Next
                                     11. Final Reflection: client's reflection verbatim, then a thoughtful family-office closing
                
                                     OBSERVATIONS
                                     Each observation section: 2–5 paragraphs of substantive analysis. Interpret, don't repeat. Strengths first. Connect values, family structure, wealth structure, and goals to show family-office thinking.
                
                                     DEPTH
                                     1,000–4,000 words scaled to survey richness. Comprehensive enough for a family to read and discuss for 15–20 minutes. Return ONLY the HTML.
                """;

        String userPrompt = """
                Create a Family Wealth Clarity Report™ using the survey data below.
                Survey Data:
                __SURVEY_JSON__
                """.replace("__SURVEY_JSON__", safePayload);

        HashMap<String, String> prompt = new HashMap<>();
        prompt.put("system", systemPrompt);
        prompt.put("user", userPrompt);
        return prompt;
    }
}
