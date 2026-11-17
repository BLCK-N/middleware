package com.myrtle.middleware.services;

import com.myrtle.middleware.enums.TransactionStatus;
import com.myrtle.middleware.model.GeneralAccount;
import com.myrtle.middleware.model.ManualTransactionEntity;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceInvestmentPaymentConfirmationTest {

    @Mock private ExternalAPIs externalAPIs;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(externalAPIs);
        ReflectionTestUtils.setField(emailService, "host", "https://api.example.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.example.com");
        ReflectionTestUtils.setField(emailService, "supportEmail", "wecare@myrtleng.com");
        ReflectionTestUtils.setField(emailService, "supportPhone", "02018888888");
        ReflectionTestUtils.setField(emailService, "onboardingInternalNotifyEmail", "wecare@myrtleng.com");
    }

    @Test
    void reminderAsksOpsToConfirmReceivingBankAndEngageCustomerIfMissing() {
        User user = new User();
        user.setFirstName("Alex");
        user.setLastName("Okeke");
        user.setEmailAddress("alex@example.com");
        user.setPhoneNumber("2348012345678");

        GeneralAccount account = new GeneralAccount();
        account.setProductName("Myrtle MyNest Money Market Fund");
        account.setBankName("Access Bank");
        account.setAccountName("Myrtle Investments");
        account.setAccountNumber("0123456789");

        ManualTransactionEntity txn = new ManualTransactionEntity();
        txn.setId("txn-1");
        txn.setProductId(account.getId());
        txn.setUserPhoneNumber("2348012345678");
        txn.setTxnAmount("250000");
        txn.setTxnStatus(TransactionStatus.PENDING);
        txn.setTxnTime(LocalDateTime.of(2026, 8, 31, 10, 45));

        emailService.sendInvestmentPaymentConfirmationReminder(user, account, txn);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(externalAPIs).sendTransactionalEmail(
                eq("wecare@myrtleng.com"),
                eq("Investment Payment Confirmation Required"),
                body.capture()
        );
        String html = body.getValue();
        assertThat(html).contains("Operations Notification");
        assertThat(html).contains("A customer has indicated that payment has been made.");
        assertThat(html).contains("Kindly confirm receipt of this payment in your receiving bank.");
        assertThat(html).contains("If the payment is not received, please engage the customer.");
        assertThat(html).contains("Alex Okeke");
        assertThat(html).contains("alex@example.com");
        assertThat(html).contains("2348012345678");
        assertThat(html).contains("Myrtle MyNest Money Market Fund");
        assertThat(html).contains("250000");
        assertThat(html).contains("txn-1");
        assertThat(html).contains("Access Bank");
        assertThat(html).contains("Customer indicated payment — not verified");
        assertThat(html).contains("View Customer");
        assertThat(html).contains("https://app.example.com");
        assertThat(html).contains("not proof that payment was received");
        assertThat(html).doesNotContain("payment has been verified");
    }
}
