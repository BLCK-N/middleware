package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.request.UpdatePepRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.UserIdentityDetailsResponse;
import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.NextOfKin;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.NextOfKinRepository;
import com.myrtle.middleware.repository.OtpRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceMainBranchTest {

    @Mock
    private ExternalAPIs externalAPIs;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CorporateClientRepository corporateClientRepository;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private NextOfKinRepository nextOfKinRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        authService = new AuthService(
                responseUtil,
                null,
                externalAPIs,
                userRepository,
                corporateClientRepository,
                null,
                otpRepository,
                passwordEncoder,
                jwtUtils,
                null,
                null,
                nextOfKinRepository,
                new ValidatorUitls(responseUtil),
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    void setPinForAccountUpdatesCorporateClientWhenEmailMatchesCorporateAccount() {
        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setId("corp-id");
        corporateClient.setEmail("corp@example.com");

        when(corporateClientRepository.findByEmailIgnoreCase("corp@example.com"))
                .thenReturn(Optional.of(corporateClient));
        when(passwordEncoder.encode("1234")).thenReturn("hashed-pin");
        when(corporateClientRepository.save(any(CorporateClient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = authService.setPinForAccount("corp@example.com", "1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).contains("corporate account");
        assertThat(corporateClient.getPinHash()).isEqualTo("hashed-pin");
        assertThat(corporateClient.isPinSet()).isTrue();
        assertThat(corporateClient.isPinEnabled()).isTrue();
    }

    @Test
    void setPinForAccountUpdatesUserWhenCorporateAccountNotFound() {
        User user = new User();
        user.setEmailAddress("user@example.com");

        when(corporateClientRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("4321")).thenReturn("hashed-user-pin");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = authService.setPinForAccount("user@example.com", "4321");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getPin()).isEqualTo("hashed-user-pin");
        assertThat(user.isUsePin()).isTrue();
    }

    @Test
    void getUserIdentityDetailsReturnsAddressNinBvnAndNextOfKins() {
        User user = new User();
        user.setId("user-id");
        user.setEmailAddress("user@example.com");
        user.setAddress("Lagos");
        user.setNin("12345678901");
        user.setBvn("10987654321");

        NextOfKin nextOfKin = new NextOfKin();
        nextOfKin.setId("nok-id");
        nextOfKin.setUser(user);
        nextOfKin.setFullName("Jane Doe");
        nextOfKin.setRelationship("SISTER");
        nextOfKin.setPhoneNumber("08012345678");
        nextOfKin.setEmail("jane@example.com");
        nextOfKin.setAddress("Abuja");
        nextOfKin.setPrimary(true);

        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(nextOfKinRepository.findByUser_Id("user-id")).thenReturn(List.of(nextOfKin));

        ResponseEntity<ApiResponse> response = authService.getUserIdentityDetails("USER@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserIdentityDetailsResponse payload = (UserIdentityDetailsResponse) response.getBody().getData();
        assertThat(payload.address()).isEqualTo("Lagos");
        assertThat(payload.nin()).isEqualTo("12345678901");
        assertThat(payload.bvn()).isEqualTo("10987654321");
        assertThat(payload.nextOfKins()).hasSize(1);
        assertThat(payload.nextOfKins().get(0).fullName()).isEqualTo("Jane Doe");
    }

    @Test
    void updatePepDetailsRequiresPositionWhenUserIsPep() {
        User user = new User();
        user.setEmailAddress("user@example.com");

        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<ApiResponse> response = authService.updatePepDetails(
                "user@example.com",
                new UpdatePepRequest(true, null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePepDetailsPersistsPepStateForUser() {
        User user = new User();
        user.setEmailAddress("user@example.com");

        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = authService.updatePepDetails(
                "user@example.com",
                new UpdatePepRequest(true, " Senator ")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.isPep()).isTrue();
        assertThat(user.getPepPosition()).isEqualTo("Senator");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getBody().getData();
        assertThat(payload.get("isPep")).isEqualTo(true);
        assertThat(payload.get("pepPosition")).isEqualTo("Senator");
    }

    @Test
    void migrateTierOneToThreeReturnsTierThreeForExistingCorporateAccount() {
        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setEmail("corp@example.com");
        corporateClient.setRole(Role.CORPORATE_ACCOUNT);
        corporateClient.setTier(3);

        when(userRepository.findByEmailAddress("corp@example.com")).thenReturn(Optional.empty());
        when(corporateClientRepository.findByEmailIgnoreCase("corp@example.com"))
                .thenReturn(Optional.of(corporateClient));

        ResponseEntity<ApiResponse> response = authService.migrateTierOneToThree(
                new com.myrtle.middleware.dtos.request.TierMigrationPayload(
                        "12345678901",
                        "David",
                        "Areegbe",
                        "Lagos",
                        new String[]{"proof-url"},
                        com.myrtle.middleware.enums.ProofType.ELECTRICITY_BILL
                ),
                "corp@example.com"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).contains("Corporate account already uses Tier 3 access");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getBody().getData();
        assertThat(payload.get("accountType")).isEqualTo(AccountType.CORPORATE_ACCOUNT.name());
        assertThat(payload.get("tier")).isEqualTo(3);
        verify(corporateClientRepository).findByEmailIgnoreCase("corp@example.com");
    }
}
