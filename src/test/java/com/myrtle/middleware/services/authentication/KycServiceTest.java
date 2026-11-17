package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.enums.ProofType;
import com.myrtle.middleware.enums.Status;
import com.myrtle.middleware.model.AddHolderDetails;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.AddHolderDetailsRepository;
import com.myrtle.middleware.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock private AddHolderDetailsRepository addHolderDetailsRepository;
    @Mock private UserRepository userRepository;

    private KycService kycService;

    @BeforeEach
    void setUp() {
        kycService = new KycService(addHolderDetailsRepository, userRepository);
        org.mockito.Mockito.lenient().when(addHolderDetailsRepository.save(any(AddHolderDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completeTier2UpgradePersistsIdentityVerifiedProofsWithoutPendingReview() {
        User user = user("1");
        when(addHolderDetailsRepository.findByUser(user)).thenReturn(Optional.empty());

        kycService.completeTier2Upgrade(
                user,
                ProofType.NATIONAL_ID,
                List.of("https://cdn.example.com/nin.pdf", "https://cdn.example.com/nin.pdf"),
                "12345678901"
        );

        ArgumentCaptor<AddHolderDetails> captor = ArgumentCaptor.forClass(AddHolderDetails.class);
        verify(addHolderDetailsRepository).save(captor.capture());
        AddHolderDetails saved = captor.getValue();

        assertThat(user.getTier()).isEqualTo("2");
        assertThat(user.getNin()).isEqualTo("12345678901");
        assertThat(saved.getStatus()).isEqualTo(Status.IDENTITY_VERIFIED);
        assertThat(saved.getProofType()).isEqualTo(ProofType.NATIONAL_ID);
        assertThat(saved.getProof()).containsExactly("https://cdn.example.com/nin.pdf");
        assertThat(saved.getAddress()).isEqualTo("Nigeria");
        verify(userRepository).save(user);
    }

    @Test
    void completeTier2UpgradeDoesNotMovePendingTier3RecordOutOfReview() {
        User user = user("2");
        AddHolderDetails existing = new AddHolderDetails();
        existing.setUser(user);
        existing.setStatus(Status.PENDING);
        existing.setAddress("22 Admiralty Way");
        existing.setProofType(ProofType.ELECTRICITY_BILL);
        existing.setProof(new ArrayList<>(List.of("https://cdn.example.com/utility.pdf")));
        when(addHolderDetailsRepository.findByUser(user)).thenReturn(Optional.of(existing));

        kycService.completeTier2Upgrade(
                user,
                ProofType.NATIONAL_ID,
                List.of("https://cdn.example.com/nin.pdf"),
                "12345678901"
        );

        assertThat(existing.getStatus()).isEqualTo(Status.PENDING);
        assertThat(existing.getProofType()).isEqualTo(ProofType.ELECTRICITY_BILL);
        assertThat(existing.getAddress()).isEqualTo("22 Admiralty Way");
        assertThat(existing.getProof()).containsExactly(
                "https://cdn.example.com/utility.pdf",
                "https://cdn.example.com/nin.pdf"
        );
        assertThat(user.getTier()).isEqualTo("2");
        assertThat(user.getNin()).isEqualTo("12345678901");
    }

    @Test
    void completeTier2UpgradeDoesNotDowngradeVerifiedTier3Record() {
        User user = user("3");
        AddHolderDetails existing = new AddHolderDetails();
        existing.setUser(user);
        existing.setStatus(Status.VERIFIED);
        existing.setAddress("22 Admiralty Way");
        existing.setProofType(ProofType.ELECTRICITY_BILL);
        existing.setProof(new ArrayList<>(List.of("https://cdn.example.com/utility.pdf")));
        when(addHolderDetailsRepository.findByUser(user)).thenReturn(Optional.of(existing));

        kycService.completeTier2Upgrade(
                user,
                ProofType.NATIONAL_ID,
                List.of("https://cdn.example.com/nin.pdf"),
                "12345678901"
        );

        assertThat(existing.getStatus()).isEqualTo(Status.VERIFIED);
        assertThat(existing.getProofType()).isEqualTo(ProofType.ELECTRICITY_BILL);
        assertThat(existing.getProof()).containsExactly(
                "https://cdn.example.com/utility.pdf",
                "https://cdn.example.com/nin.pdf"
        );
    }

    @Test
    void submitTier3ReviewMarksPendingAndKeepsExistingIdentityUrls() {
        User user = user("2");
        AddHolderDetails existing = new AddHolderDetails();
        existing.setUser(user);
        existing.setStatus(Status.IDENTITY_VERIFIED);
        existing.setProofType(ProofType.NATIONAL_ID);
        existing.setProof(new ArrayList<>(List.of("https://cdn.example.com/nin.pdf")));
        existing.setAddress("Nigeria");
        when(addHolderDetailsRepository.findByUser(user)).thenReturn(Optional.of(existing));

        kycService.submitTier3Review(
                user,
                "22 Admiralty Way",
                ProofType.ELECTRICITY_BILL,
                List.of("https://cdn.example.com/utility.pdf")
        );

        assertThat(existing.getStatus()).isEqualTo(Status.PENDING);
        assertThat(existing.getProofType()).isEqualTo(ProofType.ELECTRICITY_BILL);
        assertThat(existing.getAddress()).isEqualTo("22 Admiralty Way");
        assertThat(existing.getProof()).containsExactly(
                "https://cdn.example.com/nin.pdf",
                "https://cdn.example.com/utility.pdf"
        );
        assertThat(user.getTier()).isEqualTo("2");
    }

    @Test
    void mergeProofUrlsDeduplicatesAndIgnoresBlankValues() {
        List<String> existing = new ArrayList<>();
        existing.add("https://cdn.example.com/nin.pdf");
        existing.add("  ");
        existing.add(null);

        List<String> merged = KycService.mergeProofUrls(
                existing,
                List.of(" https://cdn.example.com/nin.pdf ", "https://cdn.example.com/utility.pdf")
        );

        assertThat(merged).containsExactly(
                "https://cdn.example.com/nin.pdf",
                "https://cdn.example.com/utility.pdf"
        );
    }

    private static User user(String tier) {
        User user = new User();
        user.setId("user-1");
        user.setEmailAddress("user@example.com");
        user.setTier(tier);
        return user;
    }
}
