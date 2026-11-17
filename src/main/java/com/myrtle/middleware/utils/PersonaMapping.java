package com.myrtle.middleware.utils;

import java.util.HashMap;
import java.util.Map;

public class PersonaMapping {

    public static String normalize(String text) {
        if (text == null) return "";
        return text
                .replace("?", "")
                .replace(",", "")
                .replace("“", "")
                .replace("”", "")
                .replace("'", "")
                .replace("’", "")
                .trim()
                .toLowerCase();
    }

    public static Map<String, Map<String, String>> getQuestionMappings() {

        Map<String, Map<String, String>> map = new HashMap<>();

        // SECTION 1 - Money Mindset
        // Q1: A=SA, B=EB, C=PWN, D=EB
        map.put(normalize("When you think about money, what comes to mind?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Everyday Builder",
                "C", "Private Wealth Niche",
                "D", "Everyday Builder"
        ));

        // Q2: A=EB, B=SA, C=PWN, D=EB
        map.put(normalize("If you win a lottery or got a big bonus at work, what would you do?"), Map.of(
                "A", "Everyday Builder",
                "B", "Strategic Achiever",
                "C", "Private Wealth Niche",
                "D", "Everyday Builder"
        ));

        // Q3: A=SA, B=EB, C=PWN, D=EB
        map.put(normalize("What gives you more peace of mind?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Everyday Builder",
                "C", "Private Wealth Niche",
                "D", "Everyday Builder"
        ));

        // Q4: A=SA, B=PWN, C=PWN, D=EB
        map.put(normalize("How do you usually make money decisions?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Private Wealth Niche",
                "C", "Private Wealth Niche",
                "D", "Everyday Builder"
        ));

        // SECTION 2 - Financial Rhythm
        // Q5: A=SA, B=EB, C=EB
        map.put(normalize("If your investment drops by 10% this month, what do you do?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Everyday Builder",
                "C", "Everyday Builder"
        ));

        // Q6: A=SA, B=EB, C=PWN, D=EB
        map.put(normalize("How do you approach saving and spending?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Everyday Builder",
                "C", "Private Wealth Niche",
                "D", "Everyday Builder"
        ));

        // Q7: A=EB, B=SA, C=PWN, D=SA
        map.put(normalize("When you invest, what matters most to you?"), Map.of(
                "A", "Everyday Builder",
                "B", "Strategic Achiever",
                "C", "Private Wealth Niche",
                "D", "Strategic Achiever"
        ));

        // SECTION 3 - Life Goals
        // Q8: A=EB, B=SA, C=PWN
        map.put(normalize("Which of these describes the stage you're in right now?"), Map.of(
                "A", "Everyday Builder",
                "B", "Strategic Achiever",
                "C", "Private Wealth Niche"
        ));

        // Q9: A=SA, B=EB, C=PWN
        map.put(normalize("What do you want your money to help you achieve next?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Everyday Builder",
                "C", "Private Wealth Niche"
        ));

        // Q10: A=PWN, B=SA, C=SA, D=SA
        map.put(normalize("When it comes to financial advice, what do you prefer?"), Map.of(
                "A", "Private Wealth Niche",
                "B", "Strategic Achiever",
                "C", "Strategic Achiever",
                "D", "Strategic Achiever"
        ));

        // SECTION 4 - Relationship with Wealth
        // Q11: A=SA, B=EB, C=PWN, D=EB
        map.put(normalize("How comfortable are you discussing money or investments?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Everyday Builder",
                "C", "Private Wealth Niche",
                "D", "Everyday Builder"
        ));

        // Q12: A=SA, B=EB, C=PWN, D=EB
        map.put(normalize("When you think about wealth, what comes to mind first?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Everyday Builder",
                "C", "Private Wealth Niche",
                "D", "Everyday Builder"
        ));

        // Q13 and Q14 are open-ended - not included in scored mappings

        // SECTION 5 - Decision Style
        // Q15: A=PWN, B=SA, C=EB, D=EB
        map.put(normalize("How do you prefer updates about your investments?"), Map.of(
                "A", "Private Wealth Niche",
                "B", "Strategic Achiever",
                "C", "Everyday Builder",
                "D", "Everyday Builder"
        ));

        // Q16: A=SA, B=PWN, C=EB, D=EB
        map.put(normalize("When life happens, how do you adjust your finances?"), Map.of(
                "A", "Strategic Achiever",
                "B", "Private Wealth Niche",
                "C", "Everyday Builder",
                "D", "Everyday Builder"
        ));

        return map;
    }

    public static Map<String, String> getOpenEndedQuestions() {
        Map<String, String> openEnded = new HashMap<>();
        openEnded.put(normalize("Who is your ultimate beneficial owner?"), "ultimateBeneficialOwner");
        openEnded.put(normalize("What do you wish your financial advisor understood about you?"), "advisorUnderstanding");
        return openEnded;
    }
}