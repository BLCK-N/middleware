package com.myrtle.middleware.utils;

public class RecommendationGenerator {
    public static String generateReport(String name, String persona) {
        switch (persona) {
            case "Everyday Builder":
                return """
                    Myrtle Money Audit Clinic Report
                    Hello %s,
                    You are an Everyday Builder.
                    Advisory Focus: Build consistency—savings culture, tracking tools, emergency fund.
                    """.formatted(name);
            case "Strategic Achiever":
                return """
                    Myrtle Money Audit Clinic Report
                    Hello %s,
                    You are a Strategic Achiever.
                    Advisory Focus: Goal-linked investing, automation, diversification.
                    """.formatted(name);
            case "Private Wealth Niche":
                return """
                    Myrtle Money Audit Clinic Report
                    Hello %s,
                    You are a Private Wealth Niche.
                    Advisory Focus: Estate planning, diversification, philanthropy.
                    """.formatted(name);
            default:
                return "Hello %s, thank you for completing the audit!".formatted(name);
        }
    }
}
