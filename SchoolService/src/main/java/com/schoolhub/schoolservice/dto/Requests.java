package com.schoolhub.schoolservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Request payloads for the school domain, grouped to keep the file count sane. */
public final class Requests {

    private Requests() {}

    // People - a login is created when loginPassword is present (admin sets a temp password).
    public record TeacherReq(
            @NotBlank String staffNo,
            @NotBlank String firstName,
            @NotBlank String lastName,
            String email,
            String phone,
            String status,
            String loginPassword) {}

    public record GuardianReq(
            @NotBlank String firstName,
            @NotBlank String lastName,
            String email,
            String phone,
            String loginPassword,
            List<Long> studentIds,
            String relationship) {}

    // A guardian claiming their own child by the child's login handle (admin-confirmed).
    public record GuardianClaimReq(@NotBlank String handle, String relationship) {}

    // A teacher splitting one of their classes into named sub-groups.
    public record ClassGroupReq(@NotBlank String name, List<Long> studentIds) {}

    // Non-teaching staff login (school admin, principal, bursar). Always creates a login.
    public record StaffReq(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "Temp password must be at least 8 characters") String password,
            @NotBlank String firstName,
            @NotBlank String lastName,
            String phone,
            @NotBlank String role) {}

    // Academic structure
    public record SubjectReq(@NotBlank String name, @NotBlank String code) {}

    public record ClassReq(@NotBlank String name, String levelLabel, Long classTeacherId) {}

    public record ClassSubjectReq(@NotNull Long classId, @NotNull Long subjectId, Long teacherId) {}

    // Cohort / Group / Enrollment (the new universal-model layer, alongside the classic one above)
    public record CohortReq(
            @NotNull Long schedulePeriodId,
            @NotBlank String name,
            String levelLabel,
            Long headUserId,
            Integer capacity,
            Boolean primeLevel) {}

    public record GroupReq(@NotNull Long cohortId, Long offeringId, @NotBlank String name) {}

    public record EnrollmentReq(
            @NotNull Long studentId,
            Long cohortId,
            Long offeringId,
            Long groupId,
            @NotNull Long schedulePeriodId) {}

    public record EnrollmentCloseReq(@NotBlank String status, String reason) {}

    // Org Unit / Offering / Schedule Period (self-referential trees)
    public record OrgUnitReq(Long parentId, @NotBlank String name, String typeLabel, Long ownerUserId) {}

    public record OfferingReq(Long parentId, @NotBlank String title, java.math.BigDecimal creditWeight) {}

    public record SchedulePeriodReq(
            Long parentId,
            @NotBlank String label,
            @NotNull LocalDate startDate,
            LocalDate endDate) {}

    // Workflow governance
    public record ProtestReq(String comment) {}

    // Invite/redemption
    public record ClassJoinIssueReq(@NotNull Long cohortId, Integer maxRedemptions) {}
    public record ClassJoinRedeemReq(@NotBlank String code) {}
    public record GuardianLinkReq(@NotNull Long wardUserId) {}

    // Resource / Session
    public record ResourceReq(
            @NotBlank String name,
            String kind,
            Integer capacity,
            Boolean staffOnly,
            Long parentResourceId) {}

    public record SessionReq(
            @NotNull Long cohortOfferingId,
            Long groupId,
            Long resourceId,
            String sessionType,
            String title,
            @NotNull java.time.LocalDateTime startAt,
            @NotNull java.time.LocalDateTime endAt,
            String recurrenceRule,
            Boolean override) {}

    public record SessionAttendanceReq(@NotNull Long studentId, @NotBlank String status) {}

    public record SessionRescheduleReq(
            @NotNull java.time.LocalDateTime startAt,
            @NotNull java.time.LocalDateTime endAt,
            String reason) {}

    // Progression Rule
    public record ProgressionRuleReq(
            @NotBlank String name,
            @NotBlank String ruleType,
            String scopeType,
            @NotNull Long scopeRefId,
            java.math.BigDecimal thresholdValue,
            String autoIssueCredential) {}

    public record EvaluateReq(Double manualScore) {}

    // Credential
    public record CredentialReq(
            @NotNull Long personUserId,
            @NotBlank String title,
            String criteriaRef,
            String artifactUrl) {}

    public record CredentialRevokeReq(String reason) {}

    // Records
    // classSubjectId (old model) is no longer @NotNull - a new-model assessment targets
    // cohortOfferingId instead; AcademicService validates at least one is set (Java-side,
    // matching Enrollment's own cohort/offering/group validation, no DB CHECK constraint).
    public record AssessmentReq(
            Long classSubjectId,
            @NotBlank String title,
            String term,
            Integer maxScore,
            LocalDate assessedOn,
            Long cohortOfferingId,
            Long groupId,
            Long sessionId,
            java.math.BigDecimal weight,
            java.math.BigDecimal passMarkPercent) {}

    public record ResultReq(@NotNull Long assessmentId, @NotNull Long studentId, @NotNull Double score, Boolean isResit) {}

    public record AttendanceReq(
            @NotNull Long studentId,
            Long classId,
            LocalDate onDate,
            @NotBlank String status) {}

    // Fees
    public record InvoiceReq(
            @NotNull Long studentId,
            @NotBlank String title,
            String term,
            @NotNull Integer amountNaira,
            LocalDate dueDate) {}

    public record PaymentReq(
            @NotNull Long invoiceId,
            @NotNull Integer amountNaira,
            String method,
            String reference) {}

    // School "resource point": post a payable item to many students at once.
    public record ResourcePostReq(
            @NotBlank String category,        // fee | book | participation | other
            @NotBlank String title,
            String description,
            String term,
            @NotNull Integer amountNaira,
            Boolean compulsory,               // default true
            String coverImageUrl,
            LocalDate dueDate,
            @NotBlank String audienceType,    // ALL | CLASS | STUDENT
            Long audienceId) {}               // class id or student id; null for ALL

    // Financial customization
    public record FinancialSettingsReq(@NotBlank String currencyCode, @NotBlank String currencySymbol) {}

    public record FeeCategoryReq(@NotBlank String name) {}

    public record ScholarshipRuleReq(
            @NotBlank String name,
            @NotBlank String ruleType,        // TOP_PERFORMER | ATTENDANCE_THRESHOLD | MANUAL_FLAG
            String scopeType,                 // OFFERING | COHORT
            Long scopeRefId,
            java.math.BigDecimal thresholdValue,
            java.math.BigDecimal discountPercent,
            String category) {}               // null = applies to all categories

    public record RefundReq(@NotNull Long paymentId, @NotNull Integer amountNaira, String reason) {}

    public record InstallmentRequestReq(@NotNull Integer installments, Integer intervalDays, String reason) {}

    // Calendar
    public record EventReq(
            @NotBlank String title,
            String description,
            String eventType,
            String audience,
            @NotNull LocalDate startDate,
            LocalDate endDate) {}
}
