package com.myrtle.middleware.services.questionnaire.report;

import com.myrtle.middleware.enums.LiquidityNeed;
import com.myrtle.middleware.enums.RiskProfile;
import com.myrtle.middleware.enums.WealthPersona;

/**
 * Deterministic narrative fragments for the client-facing report. Wording here
 * is advisory tone only - classifications always come from the engine.
 */
public final class ReportPhrases {

    /** Verbatim compliance language required on every generated report (spec section 10). */
    public static final String COMPLIANCE_DISCLAIMER =
            "This report is based on responses provided by the client and is intended to support an "
                    + "advisory conversation. It does not constitute final investment advice or an automatic "
                    + "product recommendation. Final recommendations remain subject to advisor review, "
                    + "suitability confirmation, KYC completion, compliance checks, source-of-funds review "
                    + "and client approval.";

    /** Deterministic sign-off, never touched by AI polish - protects against the model inventing its own. */
    public static final String SIGN_OFF = "With appreciation,<br/><strong>The Myrtle Advisory Team</strong>";

    private ReportPhrases() {
    }

    static String personaNarrative(WealthPersona persona) {
        return switch (persona) {
            case EVERYDAY_BUILDER -> "Based on your responses, you appear to be an Everyday Builder. "
                    + "This suggests that you value stability, structure and peace of mind for yourself and "
                    + "your family. You want your money to feel safe, dependable and steadily growing, with "
                    + "clear visibility of where you stand at every point.";
            case STRATEGIC_ACHIEVER -> "Based on your responses, you appear to be a Strategic Achiever. "
                    + "This suggests that you are focused on growth, family stability and building a stronger "
                    + "financial future. You are not simply looking to save money; you want your money to work "
                    + "better, create options, support your family and help you build over time.";
            case LEGACY_PRIVATE_WEALTH -> "Based on your responses, you appear to be a Legacy / Private "
                    + "Wealth client. This suggests that your focus has moved beyond growing wealth to "
                    + "preserving it, structuring it and ensuring that what you have built outlives you and "
                    + "serves the people and causes that matter to you.";
        };
    }

    static String riskNarrative(RiskProfile profile) {
        return switch (profile) {
            case CONSERVATIVE -> "Your responses suggest that protecting your capital matters more to you "
                    + "than chasing higher returns. Sudden market movements are uncomfortable for you, so your "
                    + "portfolio should prioritise safety, predictability and easy access to your funds.";
            case MODERATE -> "Your responses suggest that you prefer a careful, measured approach to "
                    + "investing. You can accept small fluctuations in exchange for steady returns, but large "
                    + "swings in value would be unsettling.";
            case BALANCED -> "Your responses suggest that you are moderately comfortable with investment "
                    + "risk, provided the investment case is properly explained. You appear willing to stay "
                    + "invested through normal market movements, while still valuing a degree of stability.";
            case GROWTH_ORIENTED -> "Your responses suggest that you are comfortable with meaningful "
                    + "investment risk in pursuit of long-term growth. You understand that markets move, and "
                    + "you are prepared to stay the course when they do.";
            case AGGRESSIVE -> "Your responses suggest a high comfort with investment risk and a clear "
                    + "focus on long-term growth. You are prepared to accept significant fluctuations along "
                    + "the way in exchange for stronger potential returns.";
        };
    }

    static String liquidityNarrative(LiquidityNeed need) {
        return switch (need) {
            case HIGH -> "You also indicated that you may need access to these funds at any time, so your "
                    + "portfolio should keep a substantial portion readily accessible.";
            case MODERATE -> "You also value liquidity and access to part of your funds, which means your "
                    + "portfolio may need a balanced structure: one part for liquidity and safety, one part "
                    + "for income, and another part for long-term growth.";
            case LOW -> "You indicated that you can leave these funds invested for one to three years, "
                    + "which opens the door to investments with stronger medium-term potential.";
            case VERY_LOW -> "You indicated that you have no immediate need for these funds, which allows "
                    + "your portfolio to take a genuinely long-term view.";
        };
    }

    static String recommendedNextStep(WealthPersona persona) {
        String base = "Schedule an advisor conversation to validate this summary, confirm your liquidity "
                + "requirement, agree the investment amount, complete KYC/source-of-funds review, and shape "
                + "the most suitable portfolio direction.";
        if (persona == WealthPersona.LEGACY_PRIVATE_WEALTH) {
            return base + " Given your legacy focus, this conversation may also cover estate planning, "
                    + "succession and family wealth structure.";
        }
        return base;
    }

    /**
     * One-line, non-generic explanation of why the engine reached each
     * classification - grounded in the same signal used by the classifier,
     * never freely generated. Used in the "What Your Responses Suggest" table.
     */
    static String personaBasis(WealthPersona persona) {
        return switch (persona) {
            case EVERYDAY_BUILDER -> "Your responses emphasised stability, structure and steady progress for "
                    + "you and your family.";
            case STRATEGIC_ACHIEVER -> "Your responses emphasised growth, family stability and building a "
                    + "stronger financial future.";
            case LEGACY_PRIVATE_WEALTH -> "Your responses emphasised preserving and structuring wealth so it "
                    + "outlives you and serves what matters to you.";
        };
    }

    static String riskBasis(RiskProfile profile) {
        return switch (profile) {
            case CONSERVATIVE -> "You indicated that protecting capital matters more to you than chasing "
                    + "higher returns.";
            case MODERATE -> "You indicated a preference for steadiness and a wish to avoid large swings in "
                    + "value.";
            case BALANCED -> "You indicated comfort with normal market movement, provided the investment "
                    + "case is properly explained.";
            case GROWTH_ORIENTED -> "You indicated comfort with meaningful investment risk in pursuit of "
                    + "long-term growth.";
            case AGGRESSIVE -> "You indicated high comfort with investment risk and a clear focus on "
                    + "long-term growth.";
        };
    }

    static String liquidityBasis(LiquidityNeed need) {
        return switch (need) {
            case HIGH -> "You indicated that you may need access to these funds at any time.";
            case MODERATE -> "You indicated that you value having part of your funds readily accessible.";
            case LOW -> "You indicated that you can leave these funds invested for one to three years.";
            case VERY_LOW -> "You indicated that you have no immediate need for these funds.";
        };
    }

    static final String DECISION_STYLE_BASIS =
            "Drawn directly from how you described making financial decisions.";

    static final String ENGAGEMENT_BASIS =
            "Drawn directly from your stated communication and update preferences.";
}
