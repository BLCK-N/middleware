package com.myrtle.middleware.services.refer;

import com.myrtle.middleware.dtos.referral.mywealth.request.AgentCreationDto;
import com.myrtle.middleware.dtos.referral.mywealth.response.AgentBalanceResponse;
import com.myrtle.middleware.dtos.referral.mywealth.response.AgentCreationResponse;
import com.myrtle.middleware.dtos.referral.response.AccrualItem;
import com.myrtle.middleware.dtos.referral.response.ReferralListItem;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.BvnCache;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.referral.AgentInviteCodeProxy;
import com.myrtle.middleware.model.referral.AgentReferrals;
import com.myrtle.middleware.model.referral.UserAgentsTable;
import com.myrtle.middleware.repository.AgentInviteCodeProxyRepository;
import com.myrtle.middleware.repository.AgentReferralRepository;
import com.myrtle.middleware.repository.BvnCacheRepo;
import com.myrtle.middleware.repository.UserAgentsRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static com.myrtle.middleware.services.third_party.ExternalAPIs.normalizeGender;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final int INVITE_CODE_MAX_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserAgentsRepository userAgentsRepository;
    private final AgentInviteCodeProxyRepository agentInviteCodeProxyRepository;
    private final AgentReferralRepository agentReferralRepository;
    private final BvnCacheRepo bvnCacheRepo;
    private final ExternalAPIs externalAPIs;
    private final ResponseUtil responseUtil;

    @Transactional
    public ResponseEntity<ApiResponse> beAnAgent(String userEmail) {
        log.info("beAnAgent | START | email={}", userEmail);

        User user = userRepository.findByEmailAddress(userEmail).orElseThrow(() -> new ResourceNotFound("User not found"));

        // Idempotency: already enrolled -> return the existing invite code.
        UserAgentsTable existingAgent = userAgentsRepository.findByUserId(user.getId()).orElse(null);
        if (existingAgent != null) {
            String existingCode = agentInviteCodeProxyRepository.findByAgentId(existingAgent.getAgentId())
                    .map(AgentInviteCodeProxy::getInviteCode)
                    .orElseGet(() -> createInviteCode(existingAgent.getAgentId(), user, existingAgent.getLabel()));
            log.info("beAnAgent | ALREADY_AGENT | email={} | agentId={}", userEmail, existingAgent.getAgentId());
            return responseUtil.success("You are already a referral agent", Map.of("inviteCode", existingCode));
        }

        AgentCreationResponse agent = externalAPIs.createAgent(buildAgentPayload(user));
        if (agent == null || agent.id() == null || agent.id().isBlank()) {
            log.error("beAnAgent | agent creation returned no id | email={}", userEmail);
            return responseUtil.badGateway("We could not enrol you as an agent. Please try again shortly.", null);
        }

        UserAgentsTable agentLink = new UserAgentsTable();
        agentLink.setUserId(user.getId());
        agentLink.setAgentId(agent.id());
        agentLink.setMyWealthRefCode(agent.refCode());
        agentLink.setEmail(user.getEmailAddress());
        agentLink.setLabel(agent.label());
        agentLink.setStatus(agent.status() != null ? agent.status() : "ACTIVE");
        agentLink.setPayoutEnabled(agent.payoutEnabled() == null || agent.payoutEnabled());
        userAgentsRepository.save(agentLink);

        String inviteCode = createInviteCode(agent.id(), user, agentLink.getLabel());

        log.info("beAnAgent | SUCCESS | email={} | agentId={} | inviteCode={}", userEmail, agent.id(), inviteCode);
        return responseUtil.created("Congratulations. You can now invite others", Map.of("inviteCode", inviteCode));
    }

    @Transactional
    public ResponseEntity<ApiResponse> getInviteCode(String userEmail) {
        User user = userRepository.findByEmailAddress(userEmail).orElseThrow(() -> new ResourceNotFound("User not found"));

        UserAgentsTable agent = userAgentsRepository.findByUserId(user.getId()).orElse(null);
        if (agent == null) {
            return responseUtil.notFound("You are not a referral agent yet", null);
        }

        String inviteCode = agentInviteCodeProxyRepository.findByAgentId(agent.getAgentId())
                .map(AgentInviteCodeProxy::getInviteCode)
                .orElseGet(() -> createInviteCode(agent.getAgentId(), user, agent.getLabel()));

        return responseUtil.success("Invite code retrieved", Map.of("inviteCode", inviteCode));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReferral(String agentId, String invitedUserId, String clientType) {
        if (agentId == null || agentId.isBlank() || invitedUserId == null) {
            return;
        }
        if (agentReferralRepository.existsByInvitedUserId(invitedUserId)) {
            return;
        }
        AgentReferrals referral = new AgentReferrals();
        referral.setAgentId(agentId);
        referral.setInvitedUserId(invitedUserId);
        referral.setClientType(clientType);
        agentReferralRepository.save(referral);
        log.info("recordReferral | agentId={} | invitedUserId={} | clientType={}", agentId, invitedUserId, clientType);
    }

    public ResponseEntity<ApiResponse> getReferralBalance(String userEmail) {
        User user = userRepository.findByEmailAddress(userEmail).orElseThrow(() -> new ResourceNotFound("User not found"));

        UserAgentsTable agent = userAgentsRepository.findByUserId(user.getId()).orElse(null);
        if (agent == null) {
            return responseUtil.notFound("You are not a referral agent yet", null);
        }

        AgentBalanceResponse balance = externalAPIs.getAgentReferralBalance(agent.getAgentId());

        Map<String, Object> data = new HashMap<>();
        data.put("currency", balance != null ? balance.currency() : "NGN");
        data.put("balance", balance != null ? balance.balance() : BigDecimal.ZERO);

        return responseUtil.success("Referral balance retrieved", data);
    }

    public ResponseEntity<ApiResponse> getBalanceAccruals(String userEmail) {
        User user = userRepository.findByEmailAddress(userEmail).orElseThrow(() -> new ResourceNotFound("User not found"));

        UserAgentsTable agent = userAgentsRepository.findByUserId(user.getId()).orElse(null);
        if (agent == null) {
            return responseUtil.notFound("You are not a referral agent yet", null);
        }

        return responseUtil.success("Referral accruals retrieved", accrualItems(agent.getAgentId()));
    }

    public String resolveAgentId(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return null;
        }
        String agentId = agentInviteCodeProxyRepository.findByInviteCodeIgnoreCase(inviteCode.trim())
                .map(AgentInviteCodeProxy::getAgentId)
                .orElse(null);
        if (agentId == null) {
            log.error("resolveAgentId | unknown invite code, ignoring | inviteCode={}", inviteCode);
        }
        return agentId;
    }

    public ResponseEntity<ApiResponse> lookupInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return responseUtil.badRequest("Invite code is not correct", null);
        }

        AgentInviteCodeProxy proxy = agentInviteCodeProxyRepository
                .findByInviteCodeIgnoreCase(inviteCode.trim())
                .orElse(null);
        if (proxy == null) {
            return responseUtil.notFound("Invite code is not correct", null);
        }

        String agentName = proxy.getLabel() != null && !proxy.getLabel().isBlank()
                ? proxy.getLabel()
                : "your referrer";

        return responseUtil.success("You were invited by " + agentName, Map.of("invitedBy", agentName));
    }

    @Transactional
    public ResponseEntity<ApiResponse> getMyReferrals(String userEmail) {
        User user = userRepository.findByEmailAddress(userEmail).orElseThrow(() -> new ResourceNotFound("User not found"));

        UserAgentsTable agent = userAgentsRepository.findByUserId(user.getId()).orElse(null);
        if (agent == null) {
            return responseUtil.notFound("You are not a referral agent yet", null);
        }

        return responseUtil.success("Referrals retrieved", referralItems(agent.getAgentId()));
    }

    public ResponseEntity<ApiResponse> getDashboard(String userEmail) {
        User user = userRepository.findByEmailAddress(userEmail).orElseThrow(() -> new ResourceNotFound("User not found"));

        UserAgentsTable agent = userAgentsRepository.findByUserId(user.getId()).orElse(null);
        if (agent == null) return responseUtil.notFound("You are not a referral agent yet", null);

        String agentId = agent.getAgentId();

        String inviteCode = agentInviteCodeProxyRepository.findByAgentId(agentId)
                .map(AgentInviteCodeProxy::getInviteCode)
                .orElseGet(() -> createInviteCode(agentId, user, agent.getLabel()));

        CompletableFuture<AgentBalanceResponse> balanceFuture = CompletableFuture.supplyAsync(() -> externalAPIs.getAgentReferralBalance(agentId));
        CompletableFuture<List<AccrualItem>> earningsFuture = CompletableFuture.supplyAsync(() -> accrualItems(agentId));
        CompletableFuture<List<ReferralListItem>> invitesFuture = CompletableFuture.supplyAsync(() -> referralItems(agentId));

        try {
            CompletableFuture.allOf(balanceFuture, earningsFuture, invitesFuture).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }

        AgentBalanceResponse balResp = balanceFuture.join();
        HashMap<String, Object> balance = new HashMap<>();
        balance.put("currency", balResp != null ? balResp.currency() : null);
        balance.put("balance", balResp != null ? balResp.balance() : BigDecimal.ZERO);

        Map<String, Object> data = new HashMap<>();
        data.put("inviteCode", inviteCode);
        data.put("balance", balance);
        data.put("earnings", earningsFuture.join());
        data.put("invites", invitesFuture.join());

        return responseUtil.success("Dashboard retrieved", data);
    }

    private List<ReferralListItem> referralItems(String agentId) {
        List<AgentReferrals> referrals = agentReferralRepository.findByAgentId(agentId);

        List<String> invitedUserIds = referrals.stream().map(AgentReferrals::getInvitedUserId).toList();
        Map<String, User> usersById = userRepository.findAllById(invitedUserIds).stream()
                .collect(Collectors.toMap(User::getId, invited -> invited));

        return referrals.stream()
                .map(referral -> {
                    User invited = usersById.get(referral.getInvitedUserId());
                    String name = invited != null ? fullName(invited) : null;
                    LocalDateTime joined = invited != null && invited.getCreatedAt() != null
                            ? invited.getCreatedAt()
                            : referral.getCreatedAt();
                    return new ReferralListItem(name, joined);
                })
                .sorted(Comparator.comparing(ReferralListItem::joined, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<AccrualItem> accrualItems(String agentId) {
        return externalAPIs.getAgentReferralAccruals(agentId).stream()
                .map(distribution -> new AccrualItem(distribution.currency(), distribution.amount(), distribution.distributionDate()))
                .toList();
    }

    @Transactional
    public ResponseEntity<ApiResponse> customizeInviteCode(String userEmail, String requestedCode) {
        User user = userRepository.findByEmailAddress(userEmail).orElseThrow(() -> new ResourceNotFound("User not found"));

        UserAgentsTable agent = userAgentsRepository.findByUserId(user.getId()).orElse(null);
        if (agent == null) {
            return responseUtil.notFound("You are not a referral agent yet", null);
        }

        String normalized = normalizeCustomCode(requestedCode);
        if (normalized == null) {
            return responseUtil.badRequest("Invite code must be 4-20 letters or numbers", null);
        }

        AgentInviteCodeProxy owner = agentInviteCodeProxyRepository.findByInviteCodeIgnoreCase(normalized).orElse(null);
        if (owner != null && !owner.getAgentId().equals(agent.getAgentId())) {
            return responseUtil.conflict("That invite code has been taken by another user", null);
        }

        AgentInviteCodeProxy proxy = agentInviteCodeProxyRepository.findByAgentId(agent.getAgentId())
                .orElseGet(() -> {
                    AgentInviteCodeProxy created = new AgentInviteCodeProxy();
                    created.setAgentId(agent.getAgentId());
                    created.setLabel(agent.getLabel());
                    return created;
                });
        proxy.setInviteCode(normalized);
        try {
            agentInviteCodeProxyRepository.saveAndFlush(proxy);
        } catch (DataIntegrityViolationException e) {
            log.warn("customizeInviteCode | code taken concurrently | email={} | inviteCode={}", userEmail, normalized);
            return responseUtil.conflict("That invite code has just been taken by another user", null);
        }

        log.info("customizeInviteCode | email={} | agentId={} | inviteCode={}", userEmail, agent.getAgentId(), normalized);
        return responseUtil.success("Invite code updated", Map.of("inviteCode", normalized));
    }

    public ResponseEntity<ApiResponse> checkInviteCodeAvailability(String requestedCode) {
        String normalized = normalizeCustomCode(requestedCode);
        if (normalized == null) {
            return responseUtil.badRequest("Invite code must be 4-20 letters or numbers", null);
        }

        if (agentInviteCodeProxyRepository.existsByInviteCodeIgnoreCase(normalized)) {
            return responseUtil.success("That invite code has been taken by another user", Map.of("available", false));
        }
        return responseUtil.success("Invite code is available", Map.of("available", true));
    }

    private String normalizeCustomCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z0-9]{4,20}") ? normalized : null;
    }

    private AgentCreationDto buildAgentPayload(User user) {
        String gender = normalizeGender(user.getGender());
        String title = "FEMALE".equals(gender) ? "Mrs" : "Mr";

        BvnCache bvnCache = user.getBvn() != null ? bvnCacheRepo.findBvnCacheByBvn(user.getBvn()) : null;
        AgentCreationDto.Address address = externalAPIs.buildAgentAddress(bvnCache, user.getAddress());

        return AgentCreationDto.builder()
                .type("INDIVIDUAL")
                .gender(gender)
                .title(title)
                .label(fullName(user))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .maritalStatus(externalAPIs.resolveAgentMaritalStatus(bvnCache))
                .birthDate(externalAPIs.resolveAgentBirthDate(bvnCache))
                .email(user.getEmailAddress())
                .mobileNo(user.getPhoneNumber())
                .address(address)
                .status("ACTIVE")
                .payoutEnabled(true)
                .build();
    }


    private String createInviteCode(String agentId, User user, String label) {
        String leadSegment = leadingLetters(user.getFirstName());
        String tailSegment = trailingLetters(user.getLastName(), 4);

        if (leadSegment.isEmpty()) {
            leadSegment = "AGT";
        }
        if (tailSegment.isEmpty()) {
            tailSegment = trailingLetters(user.getFirstName(), 3);
        }

        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = (leadSegment + randomDigits(3 + attempt) + tailSegment).toUpperCase(Locale.ROOT);
            if (agentInviteCodeProxyRepository.existsByInviteCodeIgnoreCase(candidate)) {
                continue;
            }
            AgentInviteCodeProxy proxy = new AgentInviteCodeProxy();
            proxy.setAgentId(agentId);
            proxy.setInviteCode(candidate);
            proxy.setLabel(label);
            agentInviteCodeProxyRepository.save(proxy);
            return candidate;
        }
        throw new IllegalStateException("Unable to generate a unique invite code after "
                + INVITE_CODE_MAX_ATTEMPTS + " attempts");
    }

    private String leadingLetters(String name) {
        String letters = lettersOnly(name);
        if (letters.isEmpty()) {
            return "";
        }
        String segment = letters.substring(0, Math.min(3, letters.length()));
        return segment.substring(0, 1).toUpperCase(Locale.ROOT)
                + segment.substring(1).toLowerCase(Locale.ROOT);
    }


    private String trailingLetters(String name, int max) {
        String letters = lettersOnly(name);
        if (letters.isEmpty()) {
            return "";
        }
        return letters.substring(Math.max(0, letters.length() - max)).toLowerCase(Locale.ROOT);
    }

    private String lettersOnly(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z]", "");
    }

    private String randomDigits(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    private String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        return (first + " " + last).trim();
    }
}
