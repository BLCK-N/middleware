package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.request.CreateKycDocumentRequest;
import com.myrtle.middleware.dtos.request.PatchClientRequest;
import com.myrtle.middleware.dtos.request.Tier2UpgradePayload;
import com.myrtle.middleware.dtos.request.Tier3UpgradePayLoad;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.ClientResponse;
import com.myrtle.middleware.dtos.response.KycDocument;
import com.myrtle.middleware.dtos.response.NinVerificationResponse;
import com.myrtle.middleware.enums.ProofType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.enums.Status;
import com.myrtle.middleware.model.AddHolderDetails;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.AddHolderDetailsRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.BvnCheckService;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTier2UpgradeTest {

    @Mock private EmailService emailService;
    @Mock private ExternalAPIs externalAPIs;
    @Mock private UserRepository userRepository;
    @Mock private BvnCheckService bvnCheckService;
    @Mock private AddHolderDetailsRepository addHolderDetailsRepository;
    @Mock private KycService kycService;
    @Mock private JwtUtils jwtUtils;

    @Spy private ResponseUtil responseUtil = new ResponseUtil();
    @Spy private ValidatorUitls validatorUitls = new ValidatorUitls(responseUtil);

    @InjectMocks private AuthService authService;

    @Test
    void upgradeToTier2ReturnsBadRequestWhenNinIsInvalidBeforeDocumentUpload() {
        User user = sampleUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(externalAPIs.verifyNin(any())).thenReturn(null);

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(validPayload(), "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Invalid or unverifiable NIN");
        verify(externalAPIs, never()).createKycDocument(any());
        verify(kycService, never()).completeTier2Upgrade(any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void upgradeToTier2ReturnsConflictWhenNinNameDoesNotMatchBeforeDocumentUpload() {
        User user = sampleUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(externalAPIs.verifyNin(any())).thenReturn(ninResponse("Other", "Person"));
        when(bvnCheckService.checkNameMatch(any(), any())).thenReturn(false);

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(validPayload(), "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(externalAPIs, never()).createKycDocument(any());
        verify(kycService, never()).completeTier2Upgrade(any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void upgradeToTier2RejectsBlankProofUrls() {
        User user = sampleUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));

        Tier2UpgradePayload payload = new Tier2UpgradePayload(
                "12345678901",
                "David",
                "Areegbe",
                new String[]{"  ", ""},
                ProofType.NATIONAL_ID
        );

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(payload, "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("valid proof URL");
        verify(externalAPIs, never()).verifyNin(any());
        verify(kycService, never()).completeTier2Upgrade(any(), any(), any(), any());
    }

    @Test
    void upgradeToTier2RejectsNonHttpProofUrls() {
        User user = sampleUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));

        Tier2UpgradePayload payload = new Tier2UpgradePayload(
                "12345678901",
                "David",
                "Areegbe",
                new String[]{"not-a-url", "ftp://files.example.com/nin.pdf"},
                ProofType.NATIONAL_ID
        );

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(payload, "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(externalAPIs, never()).createKycDocument(any());
    }

    @Test
    void upgradeToTier2DoesNotPromoteWhenMyWealthUploadFails() {
        User user = sampleUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        stubSuccessfulNin();
        when(externalAPIs.getKycDocuments("client-123")).thenReturn(List.of());
        when(externalAPIs.createKycDocument(any())).thenReturn(null);

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(validPayload(), "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(externalAPIs, never()).updatePatchClient(any(), any());
        verify(kycService, never()).completeTier2Upgrade(any(), any(), any(), any());
    }

    @Test
    void upgradeToTier2DoesNotPromoteWhenNinPatchFails() {
        User user = sampleUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        stubSuccessfulNin();
        when(externalAPIs.getKycDocuments("client-123")).thenReturn(List.of());
        when(externalAPIs.createKycDocument(any())).thenReturn(new KycDocument());
        when(externalAPIs.updatePatchClient(eq("client-123"), any(PatchClientRequest.class))).thenReturn(null);

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(validPayload(), "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(kycService, never()).completeTier2Upgrade(any(), any(), any(), any());
    }

    @Test
    void upgradeToTier2PersistsProofsAfterMyWealthSuccess() {
        User user = sampleUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        stubSuccessfulNin();
        when(externalAPIs.getKycDocuments("client-123")).thenReturn(List.of());
        when(externalAPIs.createKycDocument(any(CreateKycDocumentRequest.class))).thenReturn(new KycDocument());
        when(externalAPIs.updatePatchClient(eq("client-123"), any(PatchClientRequest.class)))
                .thenReturn(new ClientResponse());

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(validPayload(), "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(kycService).completeTier2Upgrade(
                user,
                ProofType.NATIONAL_ID,
                List.of("https://cdn.example.com/nin.pdf"),
                "12345678901"
        );
    }

    @Test
    void upgradeToTier2SkipsAlreadyRegisteredMyWealthDocumentsOnRetry() {
        User user = sampleUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        stubSuccessfulNin();

        KycDocument existing = new KycDocument();
        existing.setFileUrl("https://cdn.example.com/nin.pdf");
        when(externalAPIs.getKycDocuments("client-123")).thenReturn(List.of(existing));
        when(externalAPIs.updatePatchClient(eq("client-123"), any(PatchClientRequest.class)))
                .thenReturn(new ClientResponse());

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(validPayload(), "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(externalAPIs, never()).createKycDocument(any());
        verify(kycService).completeTier2Upgrade(any(), any(), eq(List.of("https://cdn.example.com/nin.pdf")), any());
    }

    @Test
    void upgradeToTier2RejectsDocumentVerifiedTier3User() {
        User user = sampleUser();
        user.setTier("3");
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<ApiResponse> response = authService.upgradeToTier2(validPayload(), "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(externalAPIs, never()).createKycDocument(any());
    }

    @Test
    void upgradeToTier3PersistsPendingProofsWithoutChangingTier() {
        User user = sampleUser();
        user.setTier("2");
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken("user@example.com")).thenReturn("token");

        Tier3UpgradePayLoad payload = new Tier3UpgradePayLoad(
                "22 Admiralty Way",
                new String[]{"https://cdn.example.com/utility.pdf", " "},
                ProofType.ELECTRICITY_BILL
        );

        ResponseEntity<ApiResponse> response = authService.upgradeToTier3(payload, "user@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getTier()).isEqualTo("2");
        verify(kycService).submitTier3Review(
                user,
                "22 Admiralty Way",
                ProofType.ELECTRICITY_BILL,
                List.of("https://cdn.example.com/utility.pdf")
        );
        verify(emailService).sendMailToCustomerCareUpgrade(any(), any(), eq("token"), eq(user));
    }

    @Test
    void upgradeToTier3RejectsBlankProofUrls() {
        User user = sampleUser();
        user.setTier("2");
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<ApiResponse> response = authService.upgradeToTier3(
                new Tier3UpgradePayLoad("22 Admiralty Way", new String[]{" "}, ProofType.ELECTRICITY_BILL),
                "user@example.com"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(kycService, never()).submitTier3Review(any(), any(), any(), any());
    }

    @Test
    void updateAddHolderStatusDoesNotPromoteIdentityVerifiedRecordToTier3() {
        User user = sampleUser();
        user.setTier("2");
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));

        AddHolderDetails details = new AddHolderDetails();
        details.setUser(user);
        details.setStatus(Status.IDENTITY_VERIFIED);
        details.setAddress("Nigeria");
        when(addHolderDetailsRepository.findByUser_EmailAddress("user@example.com"))
                .thenReturn(Optional.of(details));

        ResponseEntity<ApiResponse> response = authService.updateAddHolderStatus("user@example.com", Status.VERIFIED);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.getTier()).isEqualTo("2");
        verify(userRepository, never()).save(user);
    }

    @Test
    void updateAddHolderStatusApprovesPendingTier3Request() {
        User user = sampleUser();
        user.setTier("2");
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));

        AddHolderDetails details = new AddHolderDetails();
        details.setUser(user);
        details.setStatus(Status.PENDING);
        details.setAddress("22 Admiralty Way");
        when(addHolderDetailsRepository.findByUser_EmailAddress("user@example.com"))
                .thenReturn(Optional.of(details));

        ResponseEntity<ApiResponse> response = authService.updateAddHolderStatus("user@example.com", Status.VERIFIED);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getTier()).isEqualTo("3");
        assertThat(details.getStatus()).isEqualTo(Status.VERIFIED);
        verify(addHolderDetailsRepository).save(details);
    }

    private void stubSuccessfulNin() {
        when(externalAPIs.verifyNin(any())).thenReturn(ninResponse("David", "Areegbe"));
        when(bvnCheckService.checkNameMatch(any(), any())).thenReturn(true);
    }

    private static NinVerificationResponse ninResponse(String first, String last) {
        NinVerificationResponse.NinRecord ninRecord = new NinVerificationResponse.NinRecord();
        ninRecord.setNin("12345678901");
        ninRecord.setFirstname(first);
        ninRecord.setLastname(last);
        NinVerificationResponse ninResponse = new NinVerificationResponse();
        ninResponse.setNinRecord(ninRecord);
        return ninResponse;
    }

    private static Tier2UpgradePayload validPayload() {
        return new Tier2UpgradePayload(
                "12345678901",
                "David",
                "Areegbe",
                new String[]{"https://cdn.example.com/nin.pdf"},
                ProofType.NATIONAL_ID
        );
    }

    private User sampleUser() {
        User user = new User();
        user.setId("user-1");
        user.setEmailAddress("user@example.com");
        user.setFirstName("David");
        user.setLastName("Areegbe");
        user.setPhoneNumber("2348012345678");
        user.setTier("1");
        user.setClientId("client-123");
        user.setRole(Role.USER);
        return user;
    }
}
