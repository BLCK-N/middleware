package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.bvn.QoreIdBVNResponse;
import com.myrtle.middleware.dtos.request.PatchClientRequest;
import com.myrtle.middleware.dtos.request.UserLogin;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.BankAccountResponse;
import com.myrtle.middleware.dtos.response.ClientLoginResponse;
import com.myrtle.middleware.dtos.response.ClientResponse;
import com.myrtle.middleware.dtos.response.ExternalLoginResult;
import com.myrtle.middleware.dtos.response.InvestmentAccountResponse2;
import com.myrtle.middleware.dtos.response.NinVerificationResponse;
import com.myrtle.middleware.dtos.response.PagedLocationResponse;
import com.myrtle.middleware.enums.MY_WEALTH_ROLE;
import com.myrtle.middleware.model.BvnCache;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.WithdrawalAccount;
import com.myrtle.middleware.repository.BvnCacheRepo;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.repository.WithdrawalAccountRespository;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.BvnCheckService;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceMyWealthOnboardingTest {

    @Mock private ExternalAPIs externalAPIs;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private BvnCacheRepo bvnCacheRepo;
    @Mock private WithdrawalAccountRespository withdrawalAccountRespository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        authService = new AuthService(
                responseUtil,
                null,
                externalAPIs,
                userRepository,
                null,
                null,
                null,
                passwordEncoder,
                jwtUtils,
                null,
                null,
                null,
                new ValidatorUitls(responseUtil),
                new BvnCheckService(),
                bvnCacheRepo,
                null,
                withdrawalAccountRespository,
                null
        );
        RefreshTokenService refreshTokenService = org.mockito.Mockito.mock(RefreshTokenService.class);
        lenient().when(refreshTokenService.issueForUser(any())).thenReturn("refresh-token");
        ReflectionTestUtils.setField(authService, "refreshTokenService", refreshTokenService);
    }

    @Test
    void loginMigrationPatchesMyWealthWhenBvnDataDiffersAndSavesSettlementBanks() throws Exception {
        ClientLoginResponse wealthClient = wealthClient("John", "Wrong", "12345678901", null);
        stubSuccessfulMyWealthLogin(wealthClient);
        when(userRepository.findByEmailAddress("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByBvn("12345678901")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(jwtUtils.generateToken("john@example.com")).thenReturn("token");
        when(externalAPIs.verifyBvnPremium(any())).thenReturn(qoreIdBvn(
                "David", "Areegbe", "Oluwaseun", "MALE", "08011112222", "1990-03-11"));
        when(bvnCacheRepo.findBvnCacheByBvn("12345678901")).thenReturn(null);
        when(bvnCacheRepo.save(any(BvnCache.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(externalAPIs.updatePatchClient(eq("client-1"), any(PatchClientRequest.class)))
                .thenReturn(new ClientResponse());
        when(withdrawalAccountRespository.findByUser_Id("user-1")).thenReturn(List.of());
        when(externalAPIs.nameInquiry(any())).thenReturn(new BankAccountResponse("DAVID OLUSEGUN AREEGBE", "12345678901", "00", "sid"));
        when(withdrawalAccountRespository.save(any(WithdrawalAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = authService.login(new UserLogin("jsmith", "secret", "ip"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().getData();
        User savedUser = (User) data.get("user");
        assertThat(savedUser.getFirstName()).isEqualTo("David");
        assertThat(savedUser.getLastName()).isEqualTo("Areegbe");
        assertThat(savedUser.getMiddleName()).isEqualTo("Oluwaseun");
        assertThat(savedUser.getBvn()).isEqualTo("12345678901");
        assertThat(data.get("isBvnVerified")).isEqualTo(true);

        ArgumentCaptor<PatchClientRequest> patchCaptor = ArgumentCaptor.forClass(PatchClientRequest.class);
        verify(externalAPIs).updatePatchClient(eq("client-1"), patchCaptor.capture());
        PatchClientRequest.Contact patchedContact = patchCaptor.getValue().getContact().get(0);
        assertThat(patchedContact.getId()).isEqualTo("contact-1");
        assertThat(patchedContact.getFirstName()).isEqualTo("David");
        assertThat(patchedContact.getLastName()).isEqualTo("Areegbe");
        assertThat(patchedContact.getFinIdNo()).isEqualTo("12345678901");

        ArgumentCaptor<WithdrawalAccount> accountCaptor = ArgumentCaptor.forClass(WithdrawalAccount.class);
        verify(withdrawalAccountRespository, org.mockito.Mockito.times(2)).save(accountCaptor.capture());
        assertThat(accountCaptor.getAllValues())
                .extracting(WithdrawalAccount::getAccountNumber)
                .containsExactly("1111111111", "2222222222");
    }

    @Test
    void loginMigrationStillSavesSettlementBanksWhenNameInquiryFails() throws Exception {
        ClientLoginResponse wealthClient = wealthClient("David", "Areegbe", "12345678901", null);
        wealthClient.getContact().get(0).setMiddleName("Oluwaseun");
        wealthClient.getContact().get(0).setGender("MALE");
        wealthClient.getContact().get(0).setMobileNo("08011112222");
        wealthClient.getContact().get(0).setBirthDate(LocalDate.parse("1990-03-11"));
        wealthClient.getContact().get(0).setMaritalStatus("SINGLE");
        wealthClient.getContact().get(0).setTitle("MR");
        stubSuccessfulMyWealthLogin(wealthClient);
        when(userRepository.findByEmailAddress("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByBvn("12345678901")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(jwtUtils.generateToken("john@example.com")).thenReturn("token");
        when(externalAPIs.verifyBvnPremium(any())).thenReturn(qoreIdBvn(
                "David", "Areegbe", "Oluwaseun", "MALE", "08011112222", "1990-03-11"));
        when(bvnCacheRepo.findBvnCacheByBvn("12345678901")).thenReturn(null);
        when(bvnCacheRepo.save(any(BvnCache.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(withdrawalAccountRespository.findByUser_Id("user-1")).thenReturn(List.of());
        when(externalAPIs.nameInquiry(any())).thenReturn(null);
        when(withdrawalAccountRespository.save(any(WithdrawalAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = authService.login(new UserLogin("jsmith", "secret", "ip"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(externalAPIs, never()).updatePatchClient(anyString(), any());
        ArgumentCaptor<WithdrawalAccount> accountCaptor = ArgumentCaptor.forClass(WithdrawalAccount.class);
        verify(withdrawalAccountRespository, org.mockito.Mockito.times(2)).save(accountCaptor.capture());
        assertThat(accountCaptor.getAllValues())
                .extracting(WithdrawalAccount::getAccountName)
                .containsExactly("DAVID AREEGBE", "ADA OBI");
    }

    @Test
    void loginMigrationAppliesVerifiedNinAndPatchesMyWealthWhenNamesDiffer() throws Exception {
        ClientLoginResponse wealthClient = wealthClient("John", "Wrong", null, "22233344455");
        stubSuccessfulMyWealthLogin(wealthClient);
        when(userRepository.findByEmailAddress("john@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(jwtUtils.generateToken("john@example.com")).thenReturn("token");
        when(externalAPIs.verifyNin(any())).thenReturn(ninResponse("Ada", "Obi", "Chioma"));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(externalAPIs.updatePatchClient(eq("client-1"), any(PatchClientRequest.class)))
                .thenReturn(new ClientResponse());
        when(withdrawalAccountRespository.findByUser_Id("user-1")).thenReturn(List.of());
        when(externalAPIs.nameInquiry(any())).thenReturn(null);
        when(withdrawalAccountRespository.save(any(WithdrawalAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = authService.login(new UserLogin("jsmith", "secret", "ip"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        User savedUser = (User) ((Map<?, ?>) response.getBody().getData()).get("user");
        assertThat(savedUser.getFirstName()).isEqualTo("Ada");
        assertThat(savedUser.getLastName()).isEqualTo("Obi");
        assertThat(savedUser.getNin()).isEqualTo("22233344455");

        ArgumentCaptor<PatchClientRequest> patchCaptor = ArgumentCaptor.forClass(PatchClientRequest.class);
        verify(externalAPIs).updatePatchClient(eq("client-1"), patchCaptor.capture());
        PatchClientRequest.Contact patchedContact = patchCaptor.getValue().getContact().get(0);
        assertThat(patchedContact.getId()).isEqualTo("contact-1");
        assertThat(patchedContact.getIdType()).isEqualTo("NATIONAL_ID");
        assertThat(patchedContact.getIdNo()).isEqualTo("22233344455");
    }

    private void stubSuccessfulMyWealthLogin(ClientLoginResponse wealthClient) {
        when(externalAPIs.loginClient(any())).thenReturn(ExternalLoginResult.success(wealthClient));
        when(externalAPIs.getKycDocuments("client-1")).thenReturn(List.of());
        PagedLocationResponse branchResponse = new PagedLocationResponse();
        PagedLocationResponse.LocationResponse branch = new PagedLocationResponse.LocationResponse();
        branch.setId("branch-1");
        branchResponse.setContent(List.of(branch));
        when(externalAPIs.getBranch()).thenReturn(branchResponse);
        InvestmentAccountResponse2 investmentAccount = new InvestmentAccountResponse2();
        investmentAccount.setId("inv-1");
        when(externalAPIs.getInvestmentAccounts("client-1")).thenReturn(List.of(investmentAccount));
    }

    private ClientLoginResponse wealthClient(String firstName, String lastName, String bvn, String nin) {
        ClientLoginResponse.Contact contact = new ClientLoginResponse.Contact();
        contact.setId("contact-1");
        contact.setRole(MY_WEALTH_ROLE.INDV_OWNER);
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setEmail("john@example.com");
        contact.setMobileNo("08099998888");
        contact.setGender("FEMALE");
        contact.setFinIdNo(bvn);
        if (nin != null) {
            contact.setIdType("NATIONAL_ID");
            contact.setIdNo(nin);
        }

        ClientLoginResponse.SettlementBank primary = new ClientLoginResponse.SettlementBank();
        primary.setType("PRIMARY");
        primary.setStlBankName("GTBank");
        primary.setStlBankRoutingNo("058");
        primary.setStlBankAccountNo("1111111111");
        primary.setStlBankAccountName("David Areegbe");

        ClientLoginResponse.SettlementBank alternate = new ClientLoginResponse.SettlementBank();
        alternate.setType("ALTERNATE");
        alternate.setStlBankName("Access");
        alternate.setStlBankRoutingNo("044");
        alternate.setStlBankAccountNo("2222222222");
        alternate.setStlBankAccountName("Ada Obi");

        ClientLoginResponse response = new ClientLoginResponse();
        response.setId("client-1");
        response.setValuationCurrency("NGN");
        response.setContact(List.of(contact));
        response.setSettlementBanks(List.of(alternate, primary));
        return response;
    }

    private QoreIdBVNResponse qoreIdBvn(
            String firstName,
            String lastName,
            String middleName,
            String gender,
            String phone,
            String birthDate
    ) {
        QoreIdBVNResponse.Bvn bvn = QoreIdBVNResponse.Bvn.builder()
                .firstname(firstName)
                .lastname(lastName)
                .middlename(middleName)
                .gender(gender)
                .phone(phone)
                .birthdate(birthDate)
                .maritalStatus("Single")
                .title("Mr")
                .build();
        return QoreIdBVNResponse.builder().bvn(bvn).build();
    }

    private NinVerificationResponse ninResponse(String firstName, String lastName, String middleName) {
        NinVerificationResponse.NinRecord record = new NinVerificationResponse.NinRecord();
        record.setNin("22233344455");
        record.setFirstname(firstName);
        record.setLastname(lastName);
        record.setMiddlename(middleName);
        record.setGender("FEMALE");
        record.setPhone("08033334444");

        NinVerificationResponse.Summary.NinCheck ninCheck = new NinVerificationResponse.Summary.NinCheck();
        ninCheck.setStatus("verified");
        NinVerificationResponse.Summary summary = new NinVerificationResponse.Summary();
        summary.setNinCheck(ninCheck);

        NinVerificationResponse response = new NinVerificationResponse();
        response.setNinRecord(record);
        response.setSummary(summary);
        return response;
    }
}
