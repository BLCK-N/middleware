package com.myrtle.middleware.services.payment;

import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.enums.TransactionStatus;
import com.myrtle.middleware.model.GeneralAccount;
import com.myrtle.middleware.model.ManualTransactionEntity;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.GeneralAccountRepository;
import com.myrtle.middleware.repository.ManualTransactionRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.utils.ResponseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualPaymentServiceTest {

    private static final String EMAIL = "alex@example.com";
    private static final String PRODUCT_ID = "acct-1";

    @Mock private UserRepository userRepository;
    @Mock private GeneralAccountRepository generalAccountRepository;
    @Mock private ManualTransactionRepository manualTransactionRepository;
    @Mock private EmailService emailService;
    @Mock private UserDetails userDetails;

    private ManualPaymentService service;

    @BeforeEach
    void setUp() {
        service = new ManualPaymentService(
                new ResponseUtil(),
                userRepository,
                generalAccountRepository,
                manualTransactionRepository,
                emailService
        );
        when(userDetails.getUsername()).thenReturn(EMAIL);
    }

    @Test
    void registerPaymentKeepsPendingStatusAndNotifiesOps() {
        User user = user();
        GeneralAccount account = receivingAccount();
        when(userRepository.findByEmailAddress(EMAIL)).thenReturn(Optional.of(user));
        when(generalAccountRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(account));
        when(manualTransactionRepository.save(any(ManualTransactionEntity.class))).thenAnswer(invocation -> {
            ManualTransactionEntity txn = invocation.getArgument(0);
            txn.setId("txn-1");
            return txn;
        });

        ResponseEntity<ApiResponse> response = service.registerPayment(PRODUCT_ID, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        ArgumentCaptor<ManualTransactionEntity> saved = ArgumentCaptor.forClass(ManualTransactionEntity.class);
        verify(manualTransactionRepository).save(saved.capture());
        assertThat(saved.getValue().getTxnStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(saved.getValue().getTransactionTreated()).isFalse();
        assertThat(saved.getValue().getTxnAmount()).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data.get("status")).isEqualTo("PENDING");
        assertThat(data.get("transactionId")).isEqualTo("txn-1");

        verify(emailService).sendInvestmentPaymentConfirmationReminder(user, account, saved.getValue());
    }

    @Test
    void registerPaymentSucceedsWhenNotificationFails() {
        User user = user();
        GeneralAccount account = receivingAccount();
        when(userRepository.findByEmailAddress(EMAIL)).thenReturn(Optional.of(user));
        when(generalAccountRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(account));
        when(manualTransactionRepository.save(any(ManualTransactionEntity.class))).thenAnswer(invocation -> {
            ManualTransactionEntity txn = invocation.getArgument(0);
            txn.setId("txn-2");
            return txn;
        });
        doThrow(new RuntimeException("SES unavailable"))
                .when(emailService)
                .sendInvestmentPaymentConfirmationReminder(any(), any(), any());

        ResponseEntity<ApiResponse> response = service.registerPayment(PRODUCT_ID, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(manualTransactionRepository).save(any(ManualTransactionEntity.class));
    }

    private static User user() {
        User user = new User();
        user.setId("user-1");
        user.setFirstName("Alex");
        user.setLastName("Okeke");
        user.setEmailAddress(EMAIL);
        user.setPhoneNumber("2348012345678");
        return user;
    }

    private static GeneralAccount receivingAccount() {
        GeneralAccount account = new GeneralAccount();
        account.setId(PRODUCT_ID);
        account.setProductName("Myrtle MyNest Money Market Fund");
        account.setBankName("Access Bank");
        account.setAccountName("Myrtle Investments");
        account.setAccountNumber("0123456789");
        return account;
    }
}
