package com.myrtle.middleware.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myrtle.middleware.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per questionnaire submission version. Scalar columns are the
 * queryable classification outputs the backoffice filters on; the breakdown
 * JSON columns and both report HTMLs keep every result auditable.
 */
@Entity
@Table(name = "questionnaire_result",
        uniqueConstraints = @UniqueConstraint(name = "uq_questionnaire_client_version",
                columnNames = {"client_email", "version"}),
        indexes = {
                @Index(name = "idx_questionnaire_user_latest", columnList = "user_id, is_latest"),
                @Index(name = "idx_questionnaire_client_email", columnList = "client_email"),
                @Index(name = "idx_questionnaire_persona", columnList = "persona"),
                @Index(name = "idx_questionnaire_risk_profile", columnList = "risk_profile"),
                @Index(name = "idx_questionnaire_compliance", columnList = "compliance_status"),
                @Index(name = "idx_questionnaire_capacity", columnList = "financial_capacity"),
                @Index(name = "idx_questionnaire_income_band", columnList = "income_band"),
                @Index(name = "idx_questionnaire_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Column(name = "client_email", nullable = false)
    private String clientEmail;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "channel")
    private String channel;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "is_latest", nullable = false)
    private boolean latest = true;

    @Column(name = "config_version", nullable = false)
    private String configVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "persona", nullable = false)
    private WealthPersona persona;

    @Enumerated(EnumType.STRING)
    @Column(name = "persona_secondary")
    private WealthPersona personaSecondary;

    @Column(name = "persona_hybrid", nullable = false)
    private boolean personaHybrid;

    @Column(name = "persona_override_applied", nullable = false)
    private boolean personaOverrideApplied;

    @Column(name = "score_everyday_builder", nullable = false)
    private int scoreEverydayBuilder;

    @Column(name = "score_strategic_achiever", nullable = false)
    private int scoreStrategicAchiever;

    @Column(name = "score_legacy", nullable = false)
    private int scoreLegacy;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile", nullable = false)
    private RiskProfile riskProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile_pre_override", nullable = false)
    private RiskProfile riskProfilePreOverride;

    @Column(name = "risk_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "suitability_mismatch", nullable = false)
    private boolean suitabilityMismatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "liquidity_need", nullable = false)
    private LiquidityNeed liquidityNeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_capacity", nullable = false)
    private FinancialCapacity financialCapacity;

    @Column(name = "capacity_score", nullable = false, precision = 5, scale = 1)
    private BigDecimal capacityScore;

    @Column(name = "income_band")
    private String incomeBand;

    @Column(name = "liquid_assets_band")
    private String liquidAssetsBand;

    @Column(name = "real_estate_band")
    private String realEstateBand;

    @Column(name = "business_assets_band")
    private String businessAssetsBand;

    @Column(name = "debt_band")
    private String debtBand;

    @Column(name = "investment_horizon")
    private String investmentHorizon;

    @Column(name = "investment_experience")
    private String investmentExperience;

    @Column(name = "decision_style")
    private String decisionStyle;

    @Column(name = "communication_preference")
    private String communicationPreference;

    @Column(name = "privacy_openness")
    private String privacyOpenness;

    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_status", nullable = false)
    private ComplianceStatus complianceStatus;

    @Column(name = "ubo_status")
    private String uboStatus;

    @Column(name = "source_of_funds")
    private String sourceOfFunds;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false)
    private QuestionnaireEmailStatus emailStatus = QuestionnaireEmailStatus.PENDING;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "client_report_ai_polished", nullable = false)
    private boolean clientReportAiPolished;

    @Column(name = "admin_reviewed", nullable = false)
    private boolean adminReviewed = false;

    @Column(name = "admin_reviewed_at")
    private LocalDateTime adminReviewedAt;

    @Column(name = "admin_reviewed_by")
    private String adminReviewedBy;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "responses_json", columnDefinition = "TEXT")
    private String responsesJson;

    @Column(name = "persona_breakdown_json", columnDefinition = "TEXT")
    private String personaBreakdownJson;

    @Column(name = "risk_breakdown_json", columnDefinition = "TEXT")
    private String riskBreakdownJson;

    @Column(name = "capacity_breakdown_json", columnDefinition = "TEXT")
    private String capacityBreakdownJson;

    @Column(name = "liquidity_flags_json", columnDefinition = "TEXT")
    private String liquidityFlagsJson;

    @Column(name = "compliance_flags_json", columnDefinition = "TEXT")
    private String complianceFlagsJson;

    @Column(name = "mindset_themes_json", columnDefinition = "TEXT")
    private String mindsetThemesJson;

    @Column(name = "client_report_html", columnDefinition = "TEXT")
    private String clientReportHtml;

    @Column(name = "internal_report_html", columnDefinition = "TEXT")
    private String internalReportHtml;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
