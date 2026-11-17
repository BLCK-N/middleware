package com.myrtle.middleware.services.third_party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.myrtle.middleware.custom.GenericRequest;
import com.myrtle.middleware.custom.HttpMethodType;
import com.myrtle.middleware.dtos.bvn.QoreIdBVNResponse;
import com.myrtle.middleware.dtos.bvn.QoreIdBvnRequest;
import com.myrtle.middleware.dtos.bvn.QoreIdCacRequest;
import com.myrtle.middleware.dtos.referral.mywealth.request.AgentCreationDto;
import com.myrtle.middleware.dtos.referral.mywealth.response.AgentBalanceResponse;
import com.myrtle.middleware.dtos.referral.mywealth.response.AgentCreationResponse;
import com.myrtle.middleware.dtos.referral.mywealth.response.AgentDistributionResponse;
import com.myrtle.middleware.dtos.request.*;
import com.myrtle.middleware.dtos.response.*;
import com.myrtle.middleware.enums.Currency;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.exceptions.UpstreamServiceException;
import com.myrtle.middleware.model.BvnCache;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.NinVerificationRequest;
import com.myrtle.middleware.services.TokenService;
import com.myrtle.middleware.utils.Generators;
import com.myrtle.middleware.utils.MyWealthErrorParser;
import com.myrtle.middleware.utils.QoreIdErrorParser;
import com.myrtle.middleware.utils.ValidatorUitls;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;


@Service
@RequiredArgsConstructor
public class ExternalAPIs {

    private static final Logger log = LoggerFactory.getLogger(ExternalAPIs.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, String> NIGERIAN_STATE_CODE_BY_NAME = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("abia", "AB"),
            Map.entry("adamawa", "AD"),
            Map.entry("akwa ibom", "AK"),
            Map.entry("anambra", "AN"),
            Map.entry("bauchi", "BA"),
            Map.entry("benue", "BE"),
            Map.entry("borno", "BO"),
            Map.entry("bayelsa", "BY"),
            Map.entry("cross river", "CR"),
            Map.entry("delta", "DE"),
            Map.entry("ebonyi", "EB"),
            Map.entry("edo", "ED"),
            Map.entry("ekiti", "EK"),
            Map.entry("enugu", "EN"),
            Map.entry("abuja capital territory", "FC"),
            Map.entry("gombe", "GO"),
            Map.entry("imo", "IM"),
            Map.entry("jigawa", "JI"),
            Map.entry("kaduna", "KD"),
            Map.entry("kebbi", "KE"),
            Map.entry("kano", "KN"),
            Map.entry("kogi", "KO"),
            Map.entry("katsina", "KT"),
            Map.entry("kwara", "KW"),
            Map.entry("lagos", "LA"),
            Map.entry("nassarawa", "NA"),
            Map.entry("niger", "NI"),
            Map.entry("ogun", "OG"),
            Map.entry("ondo", "ON"),
            Map.entry("osun", "OS"),
            Map.entry("oyo", "OY"),
            Map.entry("plateau", "PL"),
            Map.entry("rivers", "RI"),
            Map.entry("sokoto", "SO"),
            Map.entry("taraba", "TA"),
            Map.entry("yobe", "YO"),
            Map.entry("zamfara", "ZA")
    ));
    private static final Map<String, String> NIGERIAN_STATE_NAME_BY_CODE = Map.ofEntries(
            Map.entry("AB", "Abia"),
            Map.entry("AD", "Adamawa"),
            Map.entry("AK", "Akwa Ibom"),
            Map.entry("AN", "Anambra"),
            Map.entry("BA", "Bauchi"),
            Map.entry("BE", "Benue"),
            Map.entry("BO", "Borno"),
            Map.entry("BY", "Bayelsa"),
            Map.entry("CR", "Cross River"),
            Map.entry("DE", "Delta"),
            Map.entry("EB", "Ebonyi"),
            Map.entry("ED", "Edo"),
            Map.entry("EK", "Ekiti"),
            Map.entry("EN", "Enugu"),
            Map.entry("FC", "Abuja Capital Territory"),
            Map.entry("GO", "Gombe"),
            Map.entry("IM", "Imo"),
            Map.entry("JI", "Jigawa"),
            Map.entry("KD", "Kaduna"),
            Map.entry("KE", "Kebbi"),
            Map.entry("KN", "Kano"),
            Map.entry("KO", "Kogi"),
            Map.entry("KT", "Katsina"),
            Map.entry("KW", "Kwara"),
            Map.entry("LA", "Lagos"),
            Map.entry("NA", "Nassarawa"),
            Map.entry("NI", "Niger"),
            Map.entry("OG", "Ogun"),
            Map.entry("ON", "Ondo"),
            Map.entry("OS", "Osun"),
            Map.entry("OY", "Oyo"),
            Map.entry("PL", "Plateau"),
            Map.entry("RI", "Rivers"),
            Map.entry("SO", "Sokoto"),
            Map.entry("TA", "Taraba"),
            Map.entry("YO", "Yobe"),
            Map.entry("ZA", "Zamfara")
    );
    private static final String DEFAULT_AGENT_BIRTH_DATE = "2000-01-01";
    private static final Map<String, String> NIGERIAN_POSTCODE_BY_STATE_CODE = Map.ofEntries(
            Map.entry("AB", "440001"),
            Map.entry("AD", "640001"),
            Map.entry("AK", "520001"),
            Map.entry("AN", "420001"),
            Map.entry("BA", "740001"),
            Map.entry("BE", "970001"),
            Map.entry("BO", "600001"),
            Map.entry("BY", "560001"),
            Map.entry("CR", "540001"),
            Map.entry("DE", "320001"),
            Map.entry("EB", "480001"),
            Map.entry("ED", "300001"),
            Map.entry("EK", "360001"),
            Map.entry("EN", "400001"),
            Map.entry("FC", "900001"),
            Map.entry("GO", "760001"),
            Map.entry("IM", "460001"),
            Map.entry("JI", "720001"),
            Map.entry("KD", "800001"),
            Map.entry("KE", "860001"),
            Map.entry("KN", "700001"),
            Map.entry("KO", "260001"),
            Map.entry("KT", "820001"),
            Map.entry("KW", "240001"),
            Map.entry("LA", "100001"),
            Map.entry("NA", "950001"),
            Map.entry("NI", "920001"),
            Map.entry("OG", "110001"),
            Map.entry("ON", "340001"),
            Map.entry("OS", "230001"),
            Map.entry("OY", "200001"),
            Map.entry("PL", "930001"),
            Map.entry("RI", "500001"),
            Map.entry("SO", "840001"),
            Map.entry("TA", "660001"),
            Map.entry("YO", "620001"),
            Map.entry("ZA", "880001")
    );
    private final ThirdPartyLoggingRequestService genericRequestService;
    private final AsyncHttpClient asyncHttpClient;
    private final TokenService tokenService;
    private final ValidatorUitls validatorUitls;
    private final JavaMailSender mailSender;
    @Value("${app.mail.from:${spring.mail.username}}")
    private String defaultFromAddress;
    @Value("${mywealth.api.host}")
    private String HOST;
    @Value("${gemini.api.host}")
    private String GEMINI_HOST;
    @Value("${mywealth.client.group.id}")
    private String clientGroupId;
    @Value("${mywealth.agent.group.id}")
    private String agentGroupId;
    @Value("${mywealth.referral.program.id}")
    private String referralProgramId;
    @Value("${mywealth.product.id}")
    private String productID;
    @Value("${mywealth.branch.id}")
    private String branchId;
    @Value("${qoreid.api.host}")
    private String QOREID_HOST;
    @Value("${mywealth.tenant.id}")
    private String tenantId;
    @Value("${qoreid.client.id}")
    private String qoreIdClientId;
    @Value("${qoreid.secret}")
    private String qoreIdSecret;
    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public boolean checkIfEmailExists(String email) {
        String url = HOST + "/crm/api/v1/clients/contact/" + email;
        log.debug("checkIfEmailExists | START | email={}", email);

        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);

        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);

        try {
            ResponseEntity<String> response = genericRequestService.sendRequest(request, String.class);
            boolean exists = response.getStatusCode().is2xxSuccessful();
            log.debug("checkIfEmailExists | RESULT | email={} | exists={}", email, exists);
            return exists;
        } catch (Exception e) {
            log.warn("checkIfEmailExists | FAILED, treating as not-existing | email={} | reason={}", email, e.getMessage());
            return false;
        }
    }

    public void sendTransactionalEmail(String to, String subject, String body) {
        try {
            dispatchTransactionalEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Error sending transactional email | to={} | subject={}. message: {}", to, subject, e.getMessage());
        }
    }

    /**
     * Same delivery path as {@link #sendTransactionalEmail}, but failures propagate so callers
     * can retry without rolling back unrelated business state.
     */
    public void sendTransactionalEmailStrict(String to, String subject, String body) {
        try {
            dispatchTransactionalEmail(to, subject, body);
        } catch (Exception e) {
            log.error("Error sending transactional email | to={} | subject={}", to, subject, e);
            throw new IllegalStateException("Failed to send email", e);
        }
    }

    private void dispatchTransactionalEmail(String to, String subject, String body) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(defaultFromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);
        mailSender.send(message);
    }

    public void sendTransactionalEmail2(String to, String subject, String body, String secondTo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(defaultFromAddress);
            helper.setTo(new String[]{to, secondTo});
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error sending transactional email | to={} | cc={} | subject={}. message: {}", to, secondTo, subject, e.getMessage());
        }
    }

    public List<GoalsResponse> getGoals() {
        String url = HOST + "/position/api/v1/funds/list/active/options";
        log.debug("getGoals | START | url={}", url);
        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);


        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setBody(null);
        request.setHeaders(headers);

        try {
            ResponseEntity<GoalsResponse[]> response = genericRequestService.sendRequest(request, GoalsResponse[].class);

            if (response.getBody() == null) {
                log.warn("getGoals | empty body received | httpStatus={}", response.getStatusCode().value());
                return List.of();
            }
            return Arrays.stream(response.getBody()).toList();
        } catch (Exception e) {
            log.error("getGoals | FAILED | url={} | reason={}", url, e.getMessage());
            throw e;
        }
    }

    public MyWealthClientResponse createCorporateClient(CreateCorporateClientRequest.ExternalPayload requestPayload) {
        String url = HOST + "/crm/api/v1/clients";
        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");

        GenericRequest<CreateCorporateClientRequest.ExternalPayload> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setBody(requestPayload);
        request.setHeaders(headers);

        try {
            ResponseEntity<MyWealthClientResponse> response = genericRequestService.sendRequest(request, MyWealthClientResponse.class);
            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("createCorporateClient | CLIENT_ERROR | httpStatus={} | url={} | responseBody={} | requestPayload={}",
                    e.getStatusCode().value(),
                    url,
                    e.getResponseBodyAsString(),
                    safeToJson(requestPayload),
                    e);
            if (e.getStatusCode().is4xxClientError()) {
                MyWealthErrorParser.ParsedError parsed = MyWealthErrorParser.parseClientError(
                        e.getStatusCode().value(),
                        e.getResponseBodyAsString()
                );
                throw new FunctionException(parsed.userMessage(), MyWealthErrorParser.toApiData(parsed));
            }
            throw new RuntimeException("Corporate account creation failed: " + e.getStatusCode(), e);
        } catch (HttpServerErrorException e) {
            log.error("createCorporateClient | SERVER_ERROR | httpStatus={} | url={} | responseBody={}", e.getStatusCode().value(), url, e.getResponseBodyAsString());
            throw new RuntimeException("Corporate account creation failed: downstream server error", e);
        } catch (RestClientException e) {
            log.error("createCorporateClient | RESPONSE_PARSE_ERROR | url={} | message={}", url, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Corporate client was created or processed by MyWealth, but middleware could not parse the response. Check response DTO mapping.",
                    e
            );
        } catch (Exception e) {
            log.error("createCorporateClient | UNEXPECTED_ERROR | url={} | requestPayload={}", url, safeToJson(requestPayload));
            throw new RuntimeException("Corporate account creation failed", e);
        }
    }

    public MyWealthClientResponse createIndividualClient(User user, BvnCache bvnCache, boolean includeInvestmentProduct, String agentId) {
        String url = HOST + "/crm/api/v1/clients";
        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");

        Map<String, Object> payload = buildCreationPayload(user, bvnCache, includeInvestmentProduct);
        if (agentId != null && !agentId.isBlank()) {
            payload.put("agentId", agentId);
        }

        GenericRequest<Map<String, Object>> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(payload);

        try {
            ResponseEntity<MyWealthClientResponse> response = genericRequestService.sendRequest(request, MyWealthClientResponse.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Error creating client | status={} | responseBody={} | payload={}", e.getStatusCode(), e.getResponseBodyAsString(), safeToJson(payload));
            throw new UpstreamServiceException("MyWealth", e.getStatusCode().value(),
                    "Account creation request rejected upstream. Please try again shortly.", null);
        } catch (Exception e) {
            log.error("Error creating client | payload={}", safeToJson(payload));
            throw new UpstreamServiceException("MyWealth", null, "We could not reach upstream to create your account. Please try again shortly.", null);
        }
    }


    /**
     * Registers a user as a referral agent on MyWealth by adding them to the configured agent group.
     * The {@code groupId} on the payload is forced to the configured agent group id so callers cannot
     * accidentally place the agent in the wrong group.
     *
     * @return the created agent as returned by MyWealth (carries the agent id used as the referral proxy).
     */
    public AgentCreationDto.Address buildAgentAddress(BvnCache bvnCache, String fallbackAddress) {
        String stateOfResidence = bvnCache != null ? bvnCache.getStateOfResidence() : null;
        String stateOfOrigin = bvnCache != null ? bvnCache.getStateOfOrigin() : null;
        String residentialAddress = bvnCache != null && bvnCache.getResidentialAddress() != null
                ? bvnCache.getResidentialAddress()
                : fallbackAddress;

        String stateName = resolveNigerianStateName(stateOfResidence, stateOfOrigin, residentialAddress);
        String stateCode = resolveNigerianStateCode(stateOfResidence, stateOfOrigin, residentialAddress);

        return AgentCreationDto.Address.builder()
                .type("PRIMARY")
                .addressLine1(safeText(residentialAddress, "Nigeria"))
                .city(stateName)
                .state("NG-" + stateCode)
                .postCode(NIGERIAN_POSTCODE_BY_STATE_CODE.getOrDefault(stateCode, "100001"))
                .country("NG")
                .build();
    }

    public String resolveAgentMaritalStatus(BvnCache bvnCache) {
        String normalized = bvnCache != null ? normalizeMaritalStatusForLead(bvnCache.getMaritalStatus()) : null;
        return normalized != null ? normalized : "SINGLE";
    }

    public String resolveAgentBirthDate(BvnCache bvnCache) {
        String normalized = bvnCache != null ? normalizeBirthDateForLead(bvnCache.getBirthdate()) : null;
        return normalized != null ? normalized : DEFAULT_AGENT_BIRTH_DATE;
    }

    public AgentCreationResponse createAgent(AgentCreationDto agentPayload) {
        String url = HOST + "/crm/api/v1/agents";

        agentPayload.setGroupId(agentGroupId);
        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        GenericRequest<AgentCreationDto> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(agentPayload);

        log.debug("createAgent | REQUEST | url={} | body={}", url, safeToJson(agentPayload));

        try {
            ResponseEntity<AgentCreationResponse> response =
                    genericRequestService.sendRequest(request, AgentCreationResponse.class);
            AgentCreationResponse body = response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                log.error("createAgent | non-success response | email={} | httpStatus={}", agentPayload.getEmail(), response.getStatusCode().value());
                throw new UpstreamServiceException("MyWealth", response.getStatusCode().value(),
                        "We could not enrol you as an agent. Please try again shortly.", null);
            }
            return body;
        } catch (UpstreamServiceException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("createAgent | CLIENT_ERROR | httpStatus={} | url={} | responseBody={} | payload={}",
                    e.getStatusCode().value(), url, e.getResponseBodyAsString(), safeToJson(agentPayload));
            if (e.getStatusCode().is4xxClientError()) {
                MyWealthErrorParser.ParsedError parsed = MyWealthErrorParser.parseClientError(
                        e.getStatusCode().value(), e.getResponseBodyAsString());
                throw new FunctionException(parsed.userMessage(), MyWealthErrorParser.toApiData(parsed));
            }
            throw new UpstreamServiceException("MyWealth", e.getStatusCode().value(),
                    "We could not enrol you as an agent. Please try again shortly.", null);
        } catch (Exception e) {
            log.error("createAgent | UNEXPECTED_ERROR | url={} | payload={}", url, safeToJson(agentPayload));
            throw new UpstreamServiceException("MyWealth", null,
                    "We could not enrol you as an agent. Please try again shortly.", null);
        }
    }

    public AgentBalanceResponse getAgentReferralBalance(String agentId) {
        String url = HOST + "/reports/warehouse/api/v1/pms/distributions/subject-balances?subjectServiceId=" + agentId + "&subjectType=AGENT";

        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Accept", "application/json");

        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);

        try {
            ResponseEntity<AgentBalanceResponse[]> response = genericRequestService.sendRequest(request, AgentBalanceResponse[].class);
            AgentBalanceResponse[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.warn("getAgentReferralBalance | no balances returned | agentId={}", agentId);
                return null;
            }

            AgentBalanceResponse referralBalance = Arrays.stream(body)
                    .filter(balance -> referralProgramId.equals(balance.programId()))
                    .findFirst()
                    .orElse(null);

            if (referralBalance == null) {
                log.warn("getAgentReferralBalance | no balance for referral program | agentId={} | programId={}", agentId, referralProgramId);
            }
            return referralBalance;
        } catch (Exception e) {
            log.error("getAgentReferralBalance | ERROR | agentId={} | url={} | reason={}", agentId, url, e.getMessage());
            throw new UpstreamServiceException("MyWealth", null,
                    "We could not fetch your referral balance right now. Please try again shortly.", null);
        }
    }

    public List<AgentDistributionResponse.Distribution> getAgentReferralAccruals(String agentId) {
        String url = HOST + "/reports/warehouse/api/v1/pms/distributions?programId=" + referralProgramId
                + "&size=30&subjectServiceId=" + agentId + "&subjectType=AGENT";

        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Accept", "application/json");

        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);

        try {
            ResponseEntity<AgentDistributionResponse> response = genericRequestService.sendRequest(request, AgentDistributionResponse.class);
            AgentDistributionResponse body = response.getBody();
            if (body == null || body.content() == null) {
                log.debug("getAgentReferralAccruals | no accruals returned | agentId={}", agentId);
                return List.of();
            }
            return body.content();
        } catch (Exception e) {
            log.error("getAgentReferralAccruals | ERROR | agentId={} | url={} | reason={}", agentId, url, e.getMessage());
            throw new UpstreamServiceException("MyWealth", null,
                    "We could not fetch your referral accruals right now. Please try again shortly.", null);
        }
    }

    private Map<String, Object> buildCreationPayload(User user, BvnCache bvnCache, boolean includeInvestmentProduct) {
        String normalizedMaritalStatus = normalizeMaritalStatusForLead(bvnCache.getMaritalStatus());
        if (normalizedMaritalStatus == null) {
            normalizedMaritalStatus = "SINGLE";
        }

        String normalizedGender = normalizeGenderForLead(user.getGender(), bvnCache.getGender());
        String title = "MALE".equals(normalizedGender) ? "MR" : "MRS";

        String normalizedBirthDate = normalizeBirthDateForLead(bvnCache.getBirthdate());
        if (normalizedBirthDate == null) {
            log.error("createIndividualClient aborted: no usable birth date from BVN records | email={}", user.getEmailAddress());
            throw new UpstreamServiceException("MyWealth",
                    "We could not verify your date of birth from your BVN records. Please try again or contact support.", null);
        }

        String residenceStateName = resolveNigerianStateName(
                bvnCache.getStateOfResidence(),
                bvnCache.getStateOfOrigin(),
                bvnCache.getResidentialAddress()
        );
        String residenceStateCode = resolveNigerianStateCode(
                bvnCache.getStateOfResidence(),
                bvnCache.getStateOfOrigin(),
                bvnCache.getResidentialAddress()
        );
        Map<String, Object> address = new HashMap<>();
        address.put("type", "PRIMARY");
        address.put("addressLine1", safeText(bvnCache.getResidentialAddress(), "Nigeria"));
        address.put("addressLine2", safeText(bvnCache.getResidentialAddress(), ""));
        address.put("city", residenceStateName);
        address.put("state", "NG-" + residenceStateCode);
        address.put("postCode", "100001");
        address.put("country", "NG");

        Map<String, Object> contact = new HashMap<>();
        contact.put("role", "INDV_OWNER");
        contact.put("title", title);
        contact.put("firstName", user.getFirstName());
        putIfNotBlank(contact, "middleName", user.getMiddleName());
        contact.put("lastName", user.getLastName());
        contact.put("email", user.getEmailAddress());
        contact.put("mobileNo", user.getPhoneNumber());
        contact.put("gender", normalizedGender);
        contact.put("maritalStatus", normalizedMaritalStatus);
        contact.put("birthDate", normalizedBirthDate);
        putIfNotBlank(contact, "nationality", bvnCache.getNationality());
        putIfNotBlank(contact, "stateOfOrigin", bvnCache.getStateOfOrigin());
        putIfNotBlank(contact, "localGovt", bvnCache.getLgaOfOrigin());
        contact.put("idType", "NATIONAL_ID");
        contact.put("idNo", user.getBvn());
        contact.put("finIdNo", user.getBvn());
        contact.put("grantLoginAccess", false);
        contact.put("address", address);

        Map<String, Object> payload = new HashMap<>();
        payload.put("clientType", "INDIVIDUAL");
        payload.put("email", user.getEmailAddress());
        payload.put("notificationEmail", user.getEmailAddress());
        payload.put("mobileNo", user.getPhoneNumber());
        payload.put("valuationCurrency", "NGN");
        payload.put("groupId", clientGroupId);
        payload.put("label", user.getFirstName() + " " + user.getLastName());
        payload.put("autoApprove", true);
        payload.put("address", List.of(address));
        payload.put("contact", List.of(contact));

        if (includeInvestmentProduct) {
            Map<String, Object> productRequest = new HashMap<>();
            productRequest.put("productType", "INVESTMENT_ACCOUNT");
            productRequest.put("branchId", branchId);
            productRequest.put("productId", productID);
            payload.put("productRequests", List.of(productRequest));
        }
        return payload;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private String normalizeGenderForLead(String userGender, String bvnGender) {
        String source = userGender;
        if (source == null || source.isBlank()) {
            source = bvnGender;
        }
        return normalizeGender(source);
    }

    @NotNull
    public static String normalizeGender(String source) {
        if (source == null || source.isBlank()) {
            return "NA";
        }

        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if ("MALE".equals(normalized) || "M".equals(normalized)) {
            return "MALE";
        }
        if ("FEMALE".equals(normalized) || "F".equals(normalized)) {
            return "FEMALE";
        }
        return "NA";
    }

    private String normalizeMaritalStatusForLead(String maritalStatus) {
        if (maritalStatus == null || maritalStatus.isBlank()) {
            return null;
        }

        String normalized = maritalStatus.trim().toUpperCase(Locale.ROOT);
        if ("SINGLE".equals(normalized) || "MARRIED".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String normalizeBirthDateForLead(String rawBirthDate) {
        if (rawBirthDate == null || rawBirthDate.isBlank()) {
            return null;
        }

        String value = rawBirthDate.trim();
        List<DateTimeFormatter> supportedFormats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM-dd-yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy")
        );

        for (DateTimeFormatter formatter : supportedFormats) {
            try {
                LocalDate date = LocalDate.parse(value, formatter);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String resolveNigerianStateCode(String stateOfResidence, String stateOfOrigin, String residentialAddress) {
        String fromResidence = stateCodeFromText(stateOfResidence);
        if (fromResidence != null) {
            return fromResidence;
        }

        String fromOrigin = stateCodeFromText(stateOfOrigin);
        if (fromOrigin != null) {
            return fromOrigin;
        }

        String fromAddress = stateCodeFromText(residentialAddress);
        if (fromAddress != null) {
            return fromAddress;
        }

        return "LA";
    }

    private String resolveNigerianStateName(String stateOfResidence, String stateOfOrigin, String residentialAddress) {
        String code = resolveNigerianStateCode(stateOfResidence, stateOfOrigin, residentialAddress);
        return NIGERIAN_STATE_NAME_BY_CODE.getOrDefault(code, "Lagos");
    }

    private String stateCodeFromText(String rawValue) {
        String normalized = normalizeStateKey(rawValue);
        if (normalized == null) {
            return null;
        }

        String directMatch = NIGERIAN_STATE_CODE_BY_NAME.get(normalized);
        if (directMatch != null) {
            return directMatch;
        }

        for (Map.Entry<String, String> entry : NIGERIAN_STATE_CODE_BY_NAME.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String normalizeStateKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("federal capital territory", "abuja capital territory")
                .replace("abuja fct", "abuja capital territory")
                .replace("fct", "abuja capital territory")
                .replace("nasarawa", "nassarawa")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isEmpty() ? null : normalized;
    }

    public PagedLocationResponse getBranch() {
        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);


        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(HOST + "/investing/api/v1/branches/list");
        request.setMethod(HttpMethodType.GET);
        request.setBody(null);
        request.setHeaders(headers);

        ResponseEntity<PagedLocationResponse> response = genericRequestService.sendRequest(request, PagedLocationResponse.class);
        return response.getBody();
    }

    public InvestmentAccountResponse createAnInvestmentAccount(InvestmentAccountRequest accountRequest) {
        String url = HOST + "/investing/api/v1/investment/accounts";
        accountRequest.setProductId(productID);

        // Get token
        String accessToken = tokenService.getAccessToken();

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");

        // Generic request
        GenericRequest<InvestmentAccountRequest> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(accountRequest);

        try {
            ResponseEntity<InvestmentAccountResponse> response =
                    genericRequestService.sendRequest(request, InvestmentAccountResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("createAnInvestmentAccount | non-success response | httpStatus={}", response.getStatusCode().value());
                throw new UpstreamServiceException("MyWealth", response.getStatusCode().value(),
                        "We could not create your investment account. Please try again shortly.", null);
            }
        } catch (UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("createAnInvestmentAccount | ERROR | url={} | reason={}", url, e.getMessage());
            throw new UpstreamServiceException("MyWealth", null,
                    "We could not create your investment account. Please try again shortly.", null);
        }
    }

    public String getQoreIdAccessToken() {
        String url = QOREID_HOST + "/token";

        Map<String, String> headers = new HashMap<>();
        headers.put("accept", "text/plain");
        headers.put("content-type", "application/json");

        // Body payload
        Map<String, String> payload = new HashMap<>();
        payload.put("clientId", qoreIdClientId);
        payload.put("secret", qoreIdSecret);

        GenericRequest<Map<String, String>> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(payload);

        try {
            ResponseEntity<QoreIdTokenResponse> response = genericRequestService.sendRequest(request, QoreIdTokenResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Received QoreId access token");
                return response.getBody().getAccessToken();
            }
            log.warn("getQoreIdAccessToken | non-success response | httpStatus={}", response.getStatusCode().value());
        } catch (Exception e) {
            log.error("getQoreIdAccessToken | ERROR obtaining QoreId token | reason={}", e.getMessage());
        }

        return null;
    }

    public QoreIdBVNResponse verifyBvnPremium(QoreIdBvnRequest request) {

        String accessToken = getQoreIdAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.error("BVN Premium | Missing QoreId access token");
            throw new ResourceNotFound("Unable to authenticate BVN verification service");
        }

        String url = String.format("%s/v1/ng/identities/bvn-premium/%s?isBvnIgree=false", QOREID_HOST, request.bvn());

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        Map<String, String> payload = new HashMap<>();
        payload.put("firstname", request.firstName());
        payload.put("lastname", request.lastName());
        payload.put("phone", request.phoneNumber());

        GenericRequest<Map<String, String>> genericRequest = new GenericRequest<>();
        genericRequest.setUrl(url);
        genericRequest.setMethod(HttpMethodType.POST);
        genericRequest.setHeaders(headers);
        genericRequest.setBody(payload);

        try {
            ResponseEntity<QoreIdBVNResponse> response = genericRequestService.sendRequest(genericRequest, QoreIdBVNResponse.class);
            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            QoreIdBVNResponse body = response.getBody();

            if (!status.is2xxSuccessful()) {
                log.warn("BVN Premium | Non-success HTTP response | status={} | body={}", status, safeToJson(body));
                if (safeToJson(body).contains("Insufficient Fund")) {
                    String mailBody = """ 
                            Hello,
                            You're getting this email because users can no longer verify their BVN.
                            Kindly recharge immediately to avoid further service disruptions.
                            
                            
                            Urgent regards,
                            Backend System.
                            """;
                    sendTransactionalEmail2("praise.adeleye@myrtleng.com", "Urgent!!! QoreId Funds exhausted. Recharge now.", mailBody, "patrick.elabor@myrtleng.com");

                }
                throw new UpstreamServiceException("QoreId", "We could not validate your BVN. Please try again shortly", null);
            }

            if (body == null) {
                log.error("BVN Premium | Empty response body received");
                throw new UpstreamServiceException("QoreId", "We could not validate your BVN. Please try again shortly", null);
            }

            if (body.getBvn() == null) {
                log.warn("BVN Premium | BVN object is null in response");
            }

            return body;

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            if (ex.getResponseBodyAsString().contains("Insufficient Fund")) {
                String body = """
                        Hello,
                        You're getting this email because users can no longer verify their BVN.
                        Kindly recharge immediately to avoid further service disruptions.
                        
                        Urgent regards,
                        Backend System.
                        """;
                sendTransactionalEmail2("praise.adeleye@myrtleng.com", "Urgent!!! QoreId Funds exhausted. Recharge now.", body, "patrick.elabor@myrtleng.com");
            }
            log.error("BVN Premium | HTTP error | status={} | response={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is4xxClientError()) {
                String message = QoreIdErrorParser.extractMessage(ex.getResponseBodyAsString(),
                        "We could not validate your BVN. Please try again shortly");
                throw new FunctionException(message, null);
            }
            if (ex.getStatusCode().is5xxServerError()) {
                String message = QoreIdErrorParser.extractMessage(ex.getResponseBodyAsString(),
                        "We could not validate your BVN. Please try again shortly");
                throw new UpstreamServiceException("QoreId", message, null);
            }
        } catch (ResourceAccessException ex) {
            log.error("BVN Premium | Network/connection failure while calling QoreId");
        } catch (Exception ex) {
            log.error("BVN Premium | Unexpected system error. Error message: {}", ex.getMessage());
        }
        throw new UpstreamServiceException("QoreId", "We could not validate your BVN. Please try again shortly", null);
    }

    public QoreIdCacResponse verifyCacPremium(QoreIdCacRequest request) {
        String accessToken = getQoreIdAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.error("CAC Premium | Missing QoreId access token");
            throw new ResourceNotFound("Unable to complete CAC verification service");
        }

        String url = QOREID_HOST + "/v1/ng/identities/cac-premium";

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        Map<String, Object> payload = new HashMap<>();
        payload.put("regNumber", request.regNumber());

        GenericRequest<Map<String, Object>> genericRequest = new GenericRequest<>();
        genericRequest.setUrl(url);
        genericRequest.setMethod(HttpMethodType.POST);
        genericRequest.setHeaders(headers);
        genericRequest.setBody(payload);

        try {
            ResponseEntity<String> response = genericRequestService.sendRequest(genericRequest, String.class);

            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            String rawBody = response.getBody();

            if (!status.is2xxSuccessful()) {
                log.warn("CAC Premium | Non-success HTTP response | status={} | body={}",
                        status,
                        rawBody);
                return null;
            }

            if (rawBody == null || rawBody.isBlank()) {
                log.error("CAC Premium | Empty response body received");
                return null;
            }
            return mapper.readValue(rawBody, QoreIdCacResponse.class);

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("CAC Premium | HTTP error | status={} | response={}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            if (ex.getStatusCode().is4xxClientError()) {
                String message = QoreIdErrorParser.extractMessage(ex.getResponseBodyAsString(),
                        "We could not verify this business registration number. Please try again shortly");
                throw new FunctionException(message, null);
            }
            if (ex.getStatusCode().is5xxServerError()) {
                String message = QoreIdErrorParser.extractMessage(ex.getResponseBodyAsString(),
                        "We could not verify this business registration number. Please try again shortly");
                throw new UpstreamServiceException("QoreId", message, null);
            }
        } catch (ResourceAccessException ex) {
            log.error("CAC Premium | Network/connection failure while calling QoreId. message: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("CAC Premium | Unexpected system error. message: {}", ex.getMessage());
        }

        return null;
    }

    public NinVerificationResponse verifyNin(NinVerificationRequest request) {
        String accessToken = getQoreIdAccessToken();
        if (accessToken.isEmpty()) {
            log.error("verifyNin | Missing QoreId access token");
            throw new ResourceNotFound("NO ACCESS TOKEN");
        }

        String url = QOREID_HOST + "/v1/ng/identities/nin/" + request.idNumber();

        Map<String, String> headers = new HashMap<>();
        headers.put("accept", "application/json");
        headers.put("content-type", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        Map<String, String> payload = new HashMap<>();
        payload.put("firstname", request.firstname());
        payload.put("lastname", request.lastname());

        GenericRequest<Map<String, String>> requests = new GenericRequest<>();
        requests.setUrl(url);
        requests.setMethod(HttpMethodType.POST);
        requests.setHeaders(headers);
        requests.setBody(payload);

        try {
            ResponseEntity<NinVerificationResponse> response = genericRequestService.sendRequest(requests, NinVerificationResponse.class);
            log.debug("NIN verification request sent");
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            log.warn("verifyNin | non-success response | httpStatus={}", response.getStatusCode().value());
        } catch (HttpClientErrorException ex) {
            log.error("verifyNin | HTTP client error | status={} | response={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is4xxClientError()) {
                String message = QoreIdErrorParser.extractMessage(ex.getResponseBodyAsString(),
                        "We could not verify this NIN. Please try again shortly");
                throw new FunctionException(message, null);
            }
        } catch (HttpServerErrorException ex) {
            log.error("verifyNin | HTTP server error | status={} | response={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            String message = QoreIdErrorParser.extractMessage(ex.getResponseBodyAsString(),
                    "We could not verify this NIN. Please try again shortly");
            throw new UpstreamServiceException("QoreId", message, null);
        } catch (Exception e) {
            log.error("verifyNin | ERROR | reason={}", e.getMessage());
        }
        return null;
    }

    public String generateToken() {
        log.debug("generateToken | START | requesting Gemini auth token");
        GenericRequest<Map<String, String>> request = new GenericRequest<>();
        request.setUrl(GEMINI_HOST + "/Auth.svc/api/token");
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(Map.of("Content-Type", "application/json"));
        request.setBody(Map.of("ApiKey", geminiApiKey));

        ResponseEntity<Map> response = genericRequestService.sendRequest(request, Map.class);

        if (response.getBody() != null && "00".equals(response.getBody().get("ResponseCode"))) {
            log.debug("generateToken | SUCCESS | Gemini auth token acquired");
            return (String) response.getBody().get("Token");
        }
        log.error("generateToken | FAILED | responseCode={}", response.getBody() != null ? response.getBody().get("ResponseCode") : "null");
        throw new RuntimeException("Failed to generate token: " + response);
    }

    public FundTransactionResponse createTransaction(InvestmentOrderRequest orderRequest,
                                                     UUID idempotencyId) {
        String url = HOST + "/investing/api/v1/fund_transactions";

        // Fetch access token (just like createClientAccount)
        String accessToken = tokenService.getAccessToken();

        orderRequest.setRefCode(Generators.generateRefCode());

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-idempotency-id",
                idempotencyId != null ? idempotencyId.toString() : UUID.randomUUID().toString());
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        // Wrap into GenericRequest
        GenericRequest<InvestmentOrderRequest> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(orderRequest);

        try {
            ResponseEntity<FundTransactionResponse> response =
                    genericRequestService.sendRequest(request, FundTransactionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to create fund transaction: non-2xx status");
            }
        } catch (Exception e) {
            log.error("Error creating fund transaction. message: {}", e.getMessage());
            throw new RuntimeException("Error creating fund transaction", e);
        }
    }

    public GeminiBalanceResponse getBalance(String accountNumber) {
        log.debug("getBalance | START | accountNumber={}", accountNumber);
        String token = generateToken();
        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(GEMINI_HOST + "/Account.svc/api/balanceinquiry/" + accountNumber);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(Map.of("Authorization", "Bearer " + token));

        try {
            ResponseEntity<GeminiBalanceResponse> response =
                    genericRequestService.sendRequest(request, GeminiBalanceResponse.class);

            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                log.debug("getBalance | SUCCESS | accountNumber={}", accountNumber);
                return response.getBody(); // correctly return the body here
            }
            log.warn("getBalance | non-success response | accountNumber={} | httpStatus={}",
                    accountNumber, response != null ? response.getStatusCode().value() : "null");
        } catch (Exception e) {
            log.error("getBalance | ERROR | accountNumber={} | reason={}", accountNumber, e.getMessage());
        }
        return null;
    }

    public BankResponse getBanks() {
        log.debug("getBanks | START");
        String token = generateToken();

        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(GEMINI_HOST + "/Account.svc/api/financialinstitution");
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(Map.of("Authorization", "Bearer " + token));

        try {
            ResponseEntity<BankResponse> response =
                    genericRequestService.sendRequest(request, BankResponse.class);

            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                log.debug("getBanks | SUCCESS");
                return response.getBody(); // returns the mapped Java object
            }
            log.warn("getBanks | non-success response | httpStatus={}", response != null ? response.getStatusCode().value() : "null");
        } catch (Exception e) {
            log.error("getBanks | ERROR | reason={}", e.getMessage());
        }

        return null;
    }

    public TransactionResponse createCashTransaction(TransactionRequest transactionRequest) {
        String url = HOST + "/investing/api/v1/cash_transactions";

        String accessToken = tokenService.getAccessToken();
        GenericRequest<TransactionRequest> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setBody(transactionRequest);

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        request.setHeaders(headers);

        try {
            ResponseEntity<TransactionResponse> response = genericRequestService.sendRequest(request, TransactionResponse.class);

            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                log.error("createCashTransaction | non-success response | httpStatus={}", response != null ? response.getStatusCode().value() : "No response");
                return null;
            }
        } catch (Exception e) {
            log.error("createCashTransaction | ERROR | url={} | reason={}", url, e.getMessage());
            return null;
        }
    }

    public PostTransactionResponse postTransaction(String recordId) {
        String url = HOST + "/investing/api/v1/cash_transactions/post/" + recordId;

        String accessToken = tokenService.getAccessToken();
        java.time.Instant requestTs = java.time.Instant.now();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer ***");

        try {
            Response response = asyncHttpClient.prepare("PATCH", url)
                    .setHeader("x-tenant-id", tenantId)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Accept", "application/json")
                    .setHeader("Authorization", "Bearer " + accessToken)
                    .execute()
                    .toCompletableFuture()
                    .join();

            java.time.Instant responseTs = java.time.Instant.now();
            String responseBody = response.getResponseBody();
            int statusCode = response.getStatusCode();

            genericRequestService.saveLog(
                    "MyWealth", url, "PATCH",
                    safeToJson(headers), null,
                    statusCode, responseBody,
                    requestTs, responseTs
            );

            if (statusCode >= 200 && statusCode < 300) {
                return mapper.readValue(responseBody, PostTransactionResponse.class);
            } else {
                log.error("postTransaction | FAILED | recordId={} | status={} - {}", recordId, response.getStatusCode(), response.getStatusText());
                return null;
            }
        } catch (Exception e) {
            java.time.Instant responseTs = java.time.Instant.now();
            genericRequestService.saveLog(
                    "MyWealth", url, "PATCH",
                    safeToJson(headers), null,
                    null, e.getMessage(),
                    requestTs, responseTs
            );
            log.error("postTransaction | ERROR | recordId={} | reason={}", recordId, e.getMessage());
            return null;
        }
    }

    public GeminiAccountCreationResponse createAccount(GeminiAccountCreationRequest accountRequest) {
        String token = generateToken();
        GenericRequest<GeminiAccountCreationRequest> request = new GenericRequest<>();
        request.setUrl(GEMINI_HOST + "/Account.svc/api/account");
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + token
        ));

        request.setBody(accountRequest);
        ResponseEntity<GeminiAccountCreationResponse> response = genericRequestService.sendRequest(request, GeminiAccountCreationResponse.class);

        log.debug("Gemini account creation response: {}", response);

        if (response != null && response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }

        return null;
    }

    public BankAccountResponse nameInquiry(BankAccountRequest bankAccountRequest) {
        String token = generateToken();

        GenericRequest<BankAccountRequest> request = new GenericRequest<>();
        request.setUrl(GEMINI_HOST + "/Account.svc/api/nameinquiry");
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + token
        ));
        request.setBody(bankAccountRequest);

        try {
            // Send request via generic handler
            ResponseEntity<BankAccountResponse> response = genericRequestService.sendRequest(request, BankAccountResponse.class);

            // Return the parsed body if request is successful
            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            log.warn("nameInquiry | non-success response | accountNo={} | httpStatus={}",
                    bankAccountRequest.getAccountNo(), response != null ? response.getStatusCode().value() : "null");
        } catch (Exception e) {
            log.error("nameInquiry | ERROR | accountNo={} | reason={}", bankAccountRequest.getAccountNo(), e.getMessage());
        }

        return null;
    }

    public BankTransferResponse bankTransfer(BankTransferRequest transferRequest) {
        String token = generateToken();
        GenericRequest<BankTransferRequest> request = new GenericRequest<>();
        request.setUrl(GEMINI_HOST + "/Account.svc/api/banktransfer");
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + token
        ));
        request.setBody(transferRequest);

        try {
            ResponseEntity<BankTransferResponse> response = genericRequestService.sendRequest(request, BankTransferResponse.class);

            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            log.warn("bankTransfer | non-success response | trxnRef={} | httpStatus={}",
                    transferRequest.getTrxnReferenceNo(), response != null ? response.getStatusCode().value() : "null");
        } catch (Exception e) {
            log.error("bankTransfer | ERROR | trxnRef={} | reason={}", transferRequest.getTrxnReferenceNo(), e.getMessage());
        }

        return null;
    }

    public ClientReportResponse getClientValuationReport(String clientId) {
        String url = HOST + "/reports/crm/api/v1/clients/report/client-center/valuation/" + clientId;
        log.debug("getClientValuationReport | START | clientId={}", clientId);

        // Get access token
        String accessToken = tokenService.getAccessToken();

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Accept", "application/json");

        // Build generic request
        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);

        try {
            ResponseEntity<ClientReportResponse> response =
                    genericRequestService.sendRequest(request, ClientReportResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("getClientValuationReport | SUCCESS | clientId={}", clientId);
                return response.getBody();
            } else {
                log.error("getClientValuationReport | non-success response | clientId={} | httpStatus={}", clientId, response.getStatusCode().value());
                throw new RuntimeException("Failed to fetch client report: "
                        + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("getClientValuationReport | ERROR | clientId={} | reason={}", clientId, e.getMessage());
            throw new RuntimeException("Error fetching client report for ID: " + clientId, e);
        }
    }

    public LedgerReportResponse getLedgerReport(String accountId, String currency, String valueDate) {

        String accessToken = tokenService.getAccessToken();

        // Fixed headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);

        // Build dynamic URL
        String url = HOST + "/position/api/v1/ledgers/report/account/"
                + accountId
                + "?currency=" + currency
                + "&valueDate=" + valueDate;

        // Build the generic request
        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setBody(null);
        request.setHeaders(headers);

        // Execute the request
        ResponseEntity<LedgerReportResponse> response = genericRequestService.sendRequest(request, LedgerReportResponse.class);

        return response.getBody();
    }

    public LedgerStatementResponse getRateStats(LedgerStatementRequest ledgerStatementRequest) {
        String url = HOST + "/reports/api/v1/ledgers/fund/statement/market/"
                + ledgerStatementRequest.getMarketCode()
                + "/code/" + ledgerStatementRequest.getSecId()
                + "/account/" + ledgerStatementRequest.getAccountId()
                + "?endDate=" + ledgerStatementRequest.getEndDate()
                + "&startDate=" + ledgerStatementRequest.getStartDate();
        log.debug("getRateStats | START | accountId={} | marketCode={} | secId={}",
                ledgerStatementRequest.getAccountId(), ledgerStatementRequest.getMarketCode(), ledgerStatementRequest.getSecId());

        // Get access token
        String accessToken = tokenService.getAccessToken();

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Accept", "application/json");

        // Build generic request
        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);

        try {
            ResponseEntity<LedgerStatementResponse> response =
                    genericRequestService.sendRequest(request, LedgerStatementResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("getRateStats | SUCCESS | accountId={}", ledgerStatementRequest.getAccountId());
                return response.getBody();
            } else {
                log.error("getRateStats | non-success response | accountId={} | httpStatus={}", ledgerStatementRequest.getAccountId(), response.getStatusCode().value());
                throw new RuntimeException("Failed to fetch client report: "
                        + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("getRateStats | ERROR | accountId={} | reason={}", ledgerStatementRequest.getAccountId(), e.getMessage());
            throw new RuntimeException("Error fetching client report for ID: " + ledgerStatementRequest.getAccountId(), e);
        }
    }

    public ExternalLoginResult loginClient(LoginRequest loginRequest) {

        String url = HOST + "/crm/api/v1/clients/contact/login";
        final String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        Map<String, Object> body = new HashMap<>();
        body.put("username", loginRequest.getUsername());
        body.put("password", loginRequest.getPassword());
        body.put("sessionId", loginRequest.getSessionId());
        body.put("ipAddress", loginRequest.getIpAddress());

        GenericRequest<Map<String, Object>> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(body);

        try {
            ResponseEntity<ClientLoginResponse> response =
                    genericRequestService.sendRequest(request, ClientLoginResponse.class);

            // If HTTP status is 2xx, treat as success
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ExternalLoginResult externalLoginResult = new ExternalLoginResult();
                externalLoginResult.setData(response.getBody());
                externalLoginResult.setSuccess(true);
                return externalLoginResult;
            } else {
                // Create an ExternalErrorResponse for non-2xx responses
                ExternalLoginResult.ExternalErrorResponse error = new ExternalLoginResult().new ExternalErrorResponse();
                error.setTimestamp(LocalDateTime.now().toString());
                error.setSummary("Login failed");
                error.setCode(String.valueOf(response.getStatusCode()));
                error.setDetails("Received non-success response from CRM login API");

                log.warn("loginClient | non-success response | username={} | httpStatus={}",
                        loginRequest.getUsername(), response.getStatusCode().value());

                return ExternalLoginResult.failure("LOGIN_FAILED", error);
            }

        } catch (HttpClientErrorException.BadRequest ex) {
            // Handle validation errors (400)
            log.warn("loginClient | VALIDATION_FAILED (400) | username={} | responseBody={}",
                    loginRequest.getUsername(), ex.getResponseBodyAsString());
            ExternalLoginResult.ExternalErrorResponse error = new ExternalLoginResult().new ExternalErrorResponse();
            error.setTimestamp(LocalDateTime.now().toString());
            error.setSummary("Validation Failed");
            error.setCode("400");
            error.setDetails(ex.getResponseBodyAsString());

            // Optionally parse field errors if your API returns them
            List<ExternalValidationErrorResponse.FieldError> fieldErrors = new ArrayList<>();
            // parse field errors from ex.getResponseBodyAsString() if needed

            return ExternalLoginResult.validationFailure(error, fieldErrors);

        } catch (HttpClientErrorException.NotFound ex) {
            // MyWealth returns 404 when no client matches the given username/password
            log.warn("loginClient | NOT_FOUND (404) | username={} | responseBody={}",
                    loginRequest.getUsername(), ex.getResponseBodyAsString());
            ExternalLoginResult.ExternalErrorResponse error = new ExternalLoginResult().new ExternalErrorResponse();
            error.setTimestamp(LocalDateTime.now().toString());
            error.setSummary("Not Found");
            error.setCode("404");
            error.setDetails(ex.getResponseBodyAsString());

            return ExternalLoginResult.failure("NOT_FOUND", error);

        } catch (Exception e) {
            // Generic failure
            log.error("loginClient | UNEXPECTED_ERROR | username={} | reason={}", loginRequest.getUsername(), e.getMessage());
            ExternalLoginResult.ExternalErrorResponse error = new ExternalLoginResult().new ExternalErrorResponse();
            error.setTimestamp(LocalDateTime.now().toString());
            error.setSummary("Unexpected Error");
            error.setCode("500");
            error.setDetails(e.getMessage());

            return ExternalLoginResult.failure("LOGIN_ERROR", error);
        }
    }

    public List<KycDocument> getKycDocuments(String clientId) {

        String url = HOST + "/crm/api/v1/kyc_documents/list/" + clientId;
        log.debug("getKycDocuments | START | clientId={}", clientId);

        String accessToken = tokenService.getAccessToken();

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        // Build Generic Request
        GenericRequest<Map<String, Object>> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);
        request.setBody(new HashMap<>());

        try {
            ResponseEntity<KycDocument[]> response =
                    genericRequestService.sendRequest(request, KycDocument[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<KycDocument> docs = Arrays.asList(response.getBody());
                log.debug("getKycDocuments | SUCCESS | clientId={} | count={}", clientId, docs.size());
                return docs;
            }

            log.error("getKycDocuments | non-success response | clientId={} | httpStatus={}", clientId, response.getStatusCode().value());
            throw new RuntimeException("Failed to fetch KYC docs. Status: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("getKycDocuments | ERROR | clientId={} | reason={}", clientId, e.getMessage());
            throw new RuntimeException("Error fetching KYC documents", e);
        }
    }

    public KycDocument createKycDocument(CreateKycDocumentRequest documentRequest) {
        String url = HOST + "/crm/api/v1/kyc_documents";

        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        GenericRequest<CreateKycDocumentRequest> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(documentRequest);

        try {
            ResponseEntity<KycDocument> response = genericRequestService.sendRequest(request, KycDocument.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            log.error("createKycDocument | non-success response | clientId={} | httpStatus={}",
                    documentRequest.getClientId(), response.getStatusCode().value());
            return null;
        } catch (Exception e) {
            log.error("createKycDocument | ERROR | clientId={} | reason={}",
                    documentRequest.getClientId(), e.getMessage());
            return null;
        }
    }

    public List<InvestmentAccountResponse2> getInvestmentAccounts(String clientId) {

        String url = HOST + "/investing/api/v1/investment/accounts/list/" + clientId;
        log.debug("getInvestmentAccounts | START | clientId={}", clientId);
        String accessToken = tokenService.getAccessToken();

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + accessToken);

        // Build Generic Request
        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);

        try {
            ResponseEntity<InvestmentAccountResponse2[]> response =
                    genericRequestService.sendRequest(request, InvestmentAccountResponse2[].class);

            if (response.getStatusCode().is2xxSuccessful()) {
                List<InvestmentAccountResponse2> accounts = Arrays.asList(response.getBody());
                log.debug("getInvestmentAccounts | SUCCESS | clientId={} | count={}", clientId, accounts.size());
                return accounts;
            }

            log.error("getInvestmentAccounts | non-success response | clientId={} | httpStatus={}", clientId, response.getStatusCode().value());
            throw new RuntimeException("Failed to fetch investment accounts: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("getInvestmentAccounts | ERROR | clientId={} | reason={}", clientId, e.getMessage());
            throw new RuntimeException("Error fetching investment accounts", e);
        }
    }

    public ClientPortfolioResponse clientPortfolioResponse(String clientId, Currency currency) {

        String today = String.valueOf(LocalDate.now());
        String url = String.format("%s/reports/crm/api/v1/clients/report/statement/%s/%s/%s/%s", HOST, clientId, "2021-01-01", today, currency.name());

        log.debug("clientPortfolioResponse | START | clientId={} | currency={}", clientId, currency);
        String accessToken = tokenService.getAccessToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Accept", "application/json");

        GenericRequest<Void> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.GET);
        request.setHeaders(headers);

        try {
            ResponseEntity<ClientPortfolioResponse> response = genericRequestService.sendRequest(request, ClientPortfolioResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ClientPortfolioResponse body = response.getBody();
                body.roundAllBigDecimals();
                log.debug("clientPortfolioResponse | SUCCESS | clientId={}", clientId);
                return body;
            }

            log.error("clientPortfolioResponse | non-success response | clientId={} | httpStatus={}", clientId, response.getStatusCode().value());
            throw new RuntimeException(
                    "Failed to fetch client portfolio. Status: " + response.getStatusCode()
            );

        } catch (Exception e) {
            log.error("clientPortfolioResponse | ERROR | clientId={} | reason={}", clientId, e.getMessage());
            throw new RuntimeException("Error fetching client portfolio for ID: " + clientId, e);
        }
    }


    public UsernameAvailabilityResponse isContactUsernameAvailable(String username) {
        String url = HOST + "/crm/api/v1/clients/contact/username/is-available";
        log.debug("isContactUsernameAvailable | START | username={}", username);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + tokenService.getAccessToken());
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);

        GenericRequest<Map<String, Object>> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(payload);

        try {
            ResponseEntity<UsernameAvailabilityResponse> response = genericRequestService.sendRequest(request, UsernameAvailabilityResponse.class);

            UsernameAvailabilityResponse body = response.getBody();
            if (body == null) {
                log.warn("isContactUsernameAvailable | empty response body | username={}", username);
                return new UsernameAvailabilityResponse();
            }
            return body;

        } catch (Exception e) {
            log.error("isContactUsernameAvailable | FAILED | username={} | reason={}", username, e.getMessage());
            return new UsernameAvailabilityResponse();
        }
    }

    public FinIdLookupResponse lookupClientByFinId(String finIdNo) {
        String url = HOST + "/crm/api/v1/clients/contact/fin-id-no/get-clients";
        log.debug("lookupClientByFinId | START | finIdNo={}", maskBvn(finIdNo));

        String accessToken = tokenService.getAccessToken();

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        // Request body
        Map<String, Object> payload = new HashMap<>();
        payload.put("finIdNo", finIdNo);

        // Generic request wrapper
        GenericRequest<Map<String, Object>> request = new GenericRequest<>();
        request.setUrl(url);
        request.setMethod(HttpMethodType.POST);
        request.setHeaders(headers);
        request.setBody(payload);

        try {
            ResponseEntity<FinIdLookupResponse> response = genericRequestService.sendRequest(request, FinIdLookupResponse.class);
            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("BVN lookup failed with client error: {}", e.getResponseBodyAsString());
            throw new RuntimeException("BVN validation failed", e);

        } catch (Exception e) {
            log.error("Error performing BVN lookup. message: {}", e.getMessage());
            throw new RuntimeException("BVN lookup service unavailable", e);
        }
    }

    public ClientResponse updatePatchClient(String clientId, PatchClientRequest request) {

        String url = HOST + "/crm/api/v1/clients/partial/" + clientId;

        String accessToken = tokenService.getAccessToken();
        Instant requestTs = Instant.now();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer ***");

        try {
            String requestBody = mapper.writeValueAsString(request);
            String maskedRequestBody = validatorUitls.maskSensitiveFields(requestBody);

            Response response = asyncHttpClient.prepare("PATCH", url)
                    .setHeader("x-tenant-id", tenantId)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Accept", "application/json")
                    .setHeader("Authorization", "Bearer " + accessToken)
                    .setBody(requestBody)
                    .execute()
                    .toCompletableFuture()
                    .join();

            Instant responseTs = Instant.now();

            String responseBody = response.getResponseBody();
            String maskedResponseBody = validatorUitls.maskSensitiveFields(responseBody);
            int statusCode = response.getStatusCode();

            genericRequestService.saveLog(
                    "MyWealth", url, "PATCH",
                    safeToJson(headers), maskedRequestBody,
                    statusCode, maskedResponseBody,
                    requestTs, responseTs
            );

            if (statusCode >= 200 && statusCode < 300) {
                return mapper.readValue(responseBody, ClientResponse.class);
            } else {
                log.error("patchClient failed: {} - {} | body={}",
                        statusCode, response.getStatusText(), maskedRequestBody);
                return null;
            }

        } catch (Exception e) {

            Instant responseTs = Instant.now();

            genericRequestService.saveLog(
                    "MyWealth", url, "PATCH",
                    safeToJson(headers), validatorUitls.maskSensitiveFields(safeToJson(request)),
                    null, e.getMessage(),
                    requestTs, responseTs
            );

            log.error("Error Occurred. message: {}", e.getMessage());
            return null;
        }
    }

    public ClientResponse updateAClient2(String clientId, UpdateClientRequest request) {

        String url = HOST + "/crm/api/v1/clients/" + clientId;

        String accessToken = tokenService.getAccessToken();
        Instant requestTs = Instant.now();

        Map<String, String> headers = new HashMap<>();
        headers.put("x-tenant-id", tenantId);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer ***");

        try {
            String requestBody = mapper.writeValueAsString(request);
            String maskedRequestBody = validatorUitls.maskSensitiveFields(requestBody);

            Response response = asyncHttpClient.prepare("PATCH", url)
                    .setHeader("x-tenant-id", tenantId)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Accept", "application/json")
                    .setHeader("Authorization", "Bearer " + accessToken)
                    .setBody(requestBody) // ✅ REQUIRED
                    .execute()
                    .toCompletableFuture()
                    .join();

            Instant responseTs = Instant.now();

            String responseBody = response.getResponseBody();
            String maskedResponseBody = validatorUitls.maskSensitiveFields(responseBody);
            int statusCode = response.getStatusCode();

            genericRequestService.saveLog(
                    "MyWealth", url, "PATCH",
                    safeToJson(headers), maskedRequestBody,
                    statusCode, maskedResponseBody,
                    requestTs, responseTs
            );

            if (statusCode >= 200 && statusCode < 300) {
                return mapper.readValue(responseBody, ClientResponse.class);
            } else {
                log.error("updateClient failed: {} - {}", statusCode, response.getStatusText());
                return null;
            }

        } catch (Exception e) {

            Instant responseTs = Instant.now();

            genericRequestService.saveLog(
                    "MyWealth", url, "PATCH",
                    safeToJson(headers), validatorUitls.maskSensitiveFields(safeToJson(request)),
                    null, e.getMessage(),
                    requestTs, responseTs
            );

            log.error("Error Occurred. message: {}", e.getMessage());
            return null;
        }
    }

    private String maskBvn(String bvn) {
        if (bvn == null || bvn.length() < 4) return "****";
        return bvn.substring(0, 2) + "****" + bvn.substring(bvn.length() - 2);
    }

    private String safeToJson(Object object) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return validatorUitls.maskSensitiveFields(mapper.writeValueAsString(object));
        } catch (Exception e) {
            return "[unserializable]";
        }
    }


}
