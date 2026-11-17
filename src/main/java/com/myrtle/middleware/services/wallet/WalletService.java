package com.myrtle.middleware.services.wallet;

import com.myrtle.middleware.enums.TransactionAction;
import com.myrtle.middleware.enums.TransactionStatus;
import com.myrtle.middleware.enums.TransactionType;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.FCMToken;
import com.myrtle.middleware.model.Transaction;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.WithdrawalAccount;
import com.myrtle.middleware.repository.FCMTokenRepository;
import com.myrtle.middleware.repository.TransactionRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.repository.WithdrawalAccountRespository;
import com.myrtle.middleware.dtos.request.BankAccountRequest;
import com.myrtle.middleware.dtos.request.BankTransferRequest;
import com.myrtle.middleware.dtos.request.WebhookRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.BankAccountResponse;
import com.myrtle.middleware.dtos.response.BankTransferResponse;
import com.myrtle.middleware.dtos.response.GeminiBalanceResponse;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.corporate.CorporateUserPolicy;
import com.myrtle.middleware.services.notifications.PushNotificationService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.BvnCheckService;
import com.myrtle.middleware.utils.Generators;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final ExternalAPIs externalAPIs;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final WithdrawalAccountRespository withdrawalAccountRespository;
    private final PushNotificationService pushNotificationService;
    private final FCMTokenRepository fcmTokenRepository;
    private final ResponseUtil responseUtil;
    private final BvnCheckService bvnCheckService;
    private final CorporateUserPolicy corporateUserPolicy;

    public ResponseEntity<ApiResponse> balanceEnquiry(String email) {
        log.debug("Balance enquiry | email={}", email);
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        GeminiBalanceResponse balanceResponse = externalAPIs.getBalance(user.getAccountNumber());

        if (balanceResponse == null || !"00".equals(balanceResponse.getResponseCode())) {
            log.error("Balance enquiry failed | email={} | responseCode={}", email,
                    balanceResponse != null ? balanceResponse.getResponseCode() : "null");
            return responseUtil.badRequest("Error occurred while retrieving account balance", balanceResponse);
        }

        user.setLast_balance(balanceResponse.getBalance());
        userRepository.save(user);
        log.debug("Balance retrieved | email={} | balance={}", email, balanceResponse.getBalance());
        return responseUtil.success("Balance retrieved successfully", balanceResponse);
    }

    public ResponseEntity<ApiResponse> nameInquiry(String email, BankAccountRequest accountRequest) {
        if (corporateUserPolicy.isCorporateUser(email)) {
            log.info("Blocked name inquiry for corporate user | email={}", email);
            return responseUtil.forbidden(CorporateUserPolicy.WITHDRAWAL_ACCOUNT_BLOCKED_MESSAGE, null);
        }

        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        BankAccountResponse bankAccountResponse = externalAPIs.nameInquiry(accountRequest);

        if (bankAccountResponse == null || !"00".equals(bankAccountResponse.getResponseCode())) {
            return responseUtil.badRequest("Error occurred while retrieving account details", bankAccountResponse);
        }

        return responseUtil.success("Account Details retrieved successfully", bankAccountResponse);
    }

    public ResponseEntity<ApiResponse> addWithdrawalAccount(String email, WithdrawalAccount accountRequest) {
        log.info("Adding withdrawal account | email={} | accountNumber={}", email, accountRequest.getAccountNumber());

        if (corporateUserPolicy.isCorporateUser(email)) {
            log.info("Blocked withdrawal account creation for corporate user | email={}", email);
            return responseUtil.forbidden(CorporateUserPolicy.WITHDRAWAL_ACCOUNT_BLOCKED_MESSAGE, null);
        }

        // 1. Validate user
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        // 2. Build user's full registered name
        String fullName = Stream.of(
                        user.getFirstName(),
                        user.getMiddleName(),
                        user.getLastName()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "))
                .toUpperCase()
                .trim();

        // 3. Validate withdrawal account count limit
        List<WithdrawalAccount> existingAccounts =
                withdrawalAccountRespository.findByUser_EmailAddress(email);

        if (existingAccounts.size() >= 3) {
            return responseUtil.badRequest("You have reached the maximum limit of 3 withdrawal accounts.", null);
        }

        // 4. Validate account details via external API
        BankAccountRequest bankAccountRequest = new BankAccountRequest();
        bankAccountRequest.setAccountNo(accountRequest.getAccountNumber());
        bankAccountRequest.setBankCode(accountRequest.getBankCode());

        BankAccountResponse bankAccountResponse =
                externalAPIs.nameInquiry(bankAccountRequest);

        if (bankAccountResponse == null) {
            return responseUtil.badGateway("Unable to validate account details. External service did not respond.", null);
        }

        if (!"00".equals(bankAccountResponse.getResponseCode())) {
            return responseUtil.badRequest("Account validation failed", bankAccountResponse);
        }

        // 5. Validate account ownership using bank-verified name
        String bankAccountName = bankAccountResponse.getAccountName() == null
                ? ""
                : bankAccountResponse.getAccountName().trim().toUpperCase();


        boolean checkIfMatches = bvnCheckService.checkNameMatch(bankAccountName, fullName);

        if (!checkIfMatches) {
            log.warn("Withdrawal account rejected: name mismatch | email={} | bankName={} | registeredName={}", email, bankAccountName, fullName);
            return responseUtil.conflict("Account Name Does not Match Credentials", null);
        }

        // 6. Persist account
        accountRequest.setAccountName(bankAccountName); // use verified name
        accountRequest.setUser(user);

        WithdrawalAccount savedAccount =
                withdrawalAccountRespository.save(accountRequest);

        log.info("Withdrawal account added | email={} | accountId={}", email, savedAccount.getId());
        return responseUtil.success("Withdrawal account added successfully.", savedAccount);
    }

    public ResponseEntity<ApiResponse> fetchWithdrawalAccountsForUser(String email) {
        List<WithdrawalAccount> accounts = withdrawalAccountRespository.findByUser_EmailAddress(email);

        return responseUtil.success("Withdrawal accounts fetched successfully", accounts);
    }

    public ResponseEntity<ApiResponse> editWithdrawalAccount(String email, WithdrawalAccount updatedAccount) {
        if (corporateUserPolicy.isCorporateUser(email)) {
            log.info("Blocked withdrawal account edit for corporate user | email={}", email);
            return responseUtil.forbidden(CorporateUserPolicy.WITHDRAWAL_ACCOUNT_BLOCKED_MESSAGE, null);
        }

        // 1. Fetch the existing withdrawal account
        WithdrawalAccount existingAccount = withdrawalAccountRespository.findById(updatedAccount.getId())
                .orElseThrow(() -> new ResourceNotFound("Withdrawal account not found."));

        // 2. Ensure the account belongs to the authenticated user
        if (!existingAccount.getUser().getEmailAddress().equalsIgnoreCase(email)) {
            return responseUtil.unauthorized("You are not authorized to modify this withdrawal account.", null);
        }

        User user = existingAccount.getUser();

        // 3. Construct the user's full registered name for validation
        String fullName = String.format("%s %s %s",
                user.getFirstName() != null ? user.getFirstName().trim() : "",
                user.getMiddleName() != null ? user.getMiddleName().trim() : "",
                user.getLastName() != null ? user.getLastName().trim() : "").trim();

        boolean hasUpdates = false;

        // 4. Validate and update fields
        if (updatedAccount.getBankCode() != null && !updatedAccount.getBankCode().equals(existingAccount.getBankCode())) {
            existingAccount.setBankCode(updatedAccount.getBankCode());
            hasUpdates = true;
        }

        if (updatedAccount.getAccountNumber() != null && !updatedAccount.getAccountNumber().equals(existingAccount.getAccountNumber())) {
            existingAccount.setAccountNumber(updatedAccount.getAccountNumber());
            hasUpdates = true;
        }

        if (updatedAccount.getBankName() != null && !updatedAccount.getBankName().equals(existingAccount.getBankName())) {
            existingAccount.setBankName(updatedAccount.getBankName());
            hasUpdates = true;
        }

        // 5. Validate account name consistency with user's registered full name
        if (updatedAccount.getAccountName() != null && !updatedAccount.getAccountName().equalsIgnoreCase(existingAccount.getAccountName())) {
            // Check if user is trying to change it to something different from their full name
            if (!updatedAccount.getAccountName().equalsIgnoreCase(fullName)) {
                return responseUtil.badRequest("Account name must match your registered full name.", Map.of("expectedName", fullName));
            }

            existingAccount.setAccountName(updatedAccount.getAccountName());
            hasUpdates = true;
        }

        if (!hasUpdates) {
            return responseUtil.badRequest("No changes detected. Please update at least one field.", null);
        }

        // 6. Revalidate via external API if bank code or account number changed
        if (updatedAccount.getBankCode() != null || updatedAccount.getAccountNumber() != null) {
            BankAccountRequest bankAccountRequest = new BankAccountRequest();
            bankAccountRequest.setAccountNo(existingAccount.getAccountNumber());
            bankAccountRequest.setBankCode(existingAccount.getBankCode());

            BankAccountResponse bankAccountResponse = externalAPIs.nameInquiry(bankAccountRequest);

            if (bankAccountResponse == null) {
                return responseUtil.badGateway("Unable to verify account details. Please try again later.", null);
            }

            if (!"00".equals(bankAccountResponse.getResponseCode())) {
                return responseUtil.badRequest("Account validation failed", bankAccountResponse);
            }

            // Optional: double-check that name from external API matches user’s name
            if (!bankAccountResponse.getAccountName().equalsIgnoreCase(fullName)) {
                return responseUtil.badRequest("The provided account does not belong to you.",
                        Map.of(
                                "providedName", bankAccountResponse.getAccountName(),
                                "expectedName", fullName
                        ));
            }
        }

        // 7. Save and return success response
        WithdrawalAccount savedAccount = withdrawalAccountRespository.save(existingAccount);

        return responseUtil.success("Withdrawal account updated successfully.", savedAccount);
    }

    public ResponseEntity<ApiResponse> deleteWithdrawalAccount(String email, String accountId) {
        if (corporateUserPolicy.isCorporateUser(email)) {
            log.info("Blocked withdrawal account deletion for corporate user | email={}", email);
            return responseUtil.forbidden(CorporateUserPolicy.WITHDRAWAL_ACCOUNT_BLOCKED_MESSAGE, null);
        }

        // Fetch account only if owned by the user to avoid lazy loading the user relation
        WithdrawalAccount existingAccount = withdrawalAccountRespository
                .findByIdAndUser_EmailAddress(accountId, email)
                .orElseThrow(() -> new ResourceNotFound("Withdrawal account not found or not owned by user"));

        withdrawalAccountRespository.delete(existingAccount);

        return responseUtil.success("Withdrawal account deleted successfully", null);
    }

    public ResponseEntity<ApiResponse> withdrawalFromAccountExternal(String email, String accountId, String amount) {
        log.info("Withdrawal requested | email={} | accountId={} | amount={}", email, accountId, amount);
        // 1. Fetch User
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay(); // 00:00
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay(); // next day 00:00

        List<Transaction> transactions = transactionRepository.findByUserIdAndCreatedAtBetween(user.getId(), startOfDay, endOfDay);

        BigDecimal sum = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

// Define tier limits
        BigDecimal tierLimit;
        switch (user.getTier()) {
            case "1" -> tierLimit = BigDecimal.valueOf(500_000);   // Tier 1: 500,000
            case "2" -> tierLimit = BigDecimal.valueOf(1_000_000); // Tier 2: 1,000,000
            case "3" -> tierLimit = BigDecimal.valueOf(10_000_000);// Tier 3: 10,000,000
            default -> tierLimit = BigDecimal.ZERO;              // fallback, no withdrawals
        }

// Check if daily limit exceeded
        BigDecimal requestedAmount = BigDecimal.valueOf(Double.parseDouble(amount));
        if (sum.add(requestedAmount).compareTo(tierLimit) > 0) {
            log.warn("Daily withdrawal limit exceeded | email={} | tier={} | dailySum={} | requested={} | limit={}", email, user.getTier(), sum, requestedAmount, tierLimit);
            return responseUtil.badRequest("Daily withdrawal limit exceeded for your tier", null);
        }


        // 2. Fetch withdrawal account, ensuring it belongs to this user (prevents lazy load of user)
        WithdrawalAccount existingAccount = withdrawalAccountRespository
                .findByIdAndUser_EmailAddress(accountId, email)
                .orElseThrow(() -> new ResourceNotFound("Withdrawal account not found or not owned by user"));

        // 4. Validate balance (assuming user.getLastBalance() is available)
        double withdrawAmount = Double.parseDouble(amount);
        if (user.getLast_balance() < withdrawAmount) {
            return responseUtil.badRequest("Insufficient funds", null);
        }

        BankAccountRequest bankAccountRequest = new BankAccountRequest();
        bankAccountRequest.setBankCode(existingAccount.getBankCode());
        bankAccountRequest.setAccountNo(existingAccount.getAccountNumber());
        BankAccountResponse bankAccountResponse = externalAPIs.nameInquiry(bankAccountRequest);

        if (bankAccountResponse == null || bankAccountResponse.getAccountName() == null) {
            return responseUtil.conflict("Error Creating Investment with External API", null);
        }

        // 5. Build BankTransferRequest
        BankTransferRequest request = new BankTransferRequest();
        request.setAccountNo(user.getAccountNumber());
        request.setAmount(amount);
        request.setBeneficiaryAccountName(existingAccount.getAccountName());
        request.setBeneficiaryAccountNumber(existingAccount.getAccountNumber());
        request.setNarration("Withdrawal of " + amount + " from account");
        request.setTrxnFee("0");
        request.setMerchantCode("MYTLE");
        request.setNameEnquiryRef(bankAccountResponse.getSessionId());
        request.setTrxnReferenceNo(Generators.generateTrxnRef("000002")); // professional generator
        request.setDestinationInstitutionCode(existingAccount.getBankCode());
        request.setBeneficiaryBankVerificationNumber(existingAccount.getBankVerificationNumber());

        // 6. Call external API
        BankTransferResponse bankTransferResponse = externalAPIs.bankTransfer(request);

        // 7. Handle API Response
        if (bankTransferResponse != null && "00".equals(bankTransferResponse.getResponseCode())) {
            // Deduct balance after successful transfer
            user.setLast_balance(user.getLast_balance() - withdrawAmount);
            userRepository.save(user);

            log.info("Withdrawal successful | email={} | amount={} | trxnRef={}", email, amount, bankTransferResponse.getTrxnRefNo());
            return responseUtil.success("Withdrawal successful", bankTransferResponse);
        }
        log.error("Withdrawal bank transfer failed | email={} | amount={} | responseCode={}", email, amount,
                bankTransferResponse != null ? bankTransferResponse.getResponseCode() : "null");

        Transaction withdrawalTransaction = new Transaction();
        withdrawalTransaction.setType(TransactionType.WALLET_WITHDRAWAL);
        withdrawalTransaction.setAmount(BigDecimal.valueOf(Double.parseDouble(amount)));
        withdrawalTransaction.setTxRef(bankTransferResponse != null ? bankTransferResponse.getTrxnRefNo() : null);
        withdrawalTransaction.setCreatedAt(LocalDateTime.now());
        withdrawalTransaction.setStatus(TransactionStatus.SUCCESS);
        withdrawalTransaction.setUser(user);
        withdrawalTransaction.setAction(TransactionAction.DEBIT);
        withdrawalTransaction.setNarration("Withdrawal from Wallet");
        withdrawalTransaction.setSource("Wallet");
        transactionRepository.save(withdrawalTransaction);

        return responseUtil.badRequest("Withdrawal failed: " +
                (bankTransferResponse != null ? bankTransferResponse.getResponseMessage() : "Unknown error"), bankTransferResponse);
    }

    public ResponseEntity<ApiResponse> updateTransactionsHistory(WebhookRequest webhookRequest) {
        log.info("Webhook received | trxnRef={} | beneficiaryAccount={} | amount={}", webhookRequest.getTrxnReferenceNo(), webhookRequest.getBeneficiaryAccountNo(), webhookRequest.getAmount());

        //  Detect the user account involved (beneficiary or originator)
        User user = userRepository.findByAccountNumber(webhookRequest.getBeneficiaryAccountNo())
                .or(() -> userRepository.findByAccountNumber(webhookRequest.getOriginatorAccountNo()))
                .orElseThrow(() ->
                        new ResourceNotFound("User not found for provided account numbers")
                );

        //  Determine transaction action
        TransactionAction action = user.getAccountNumber()
                .equals(webhookRequest.getBeneficiaryAccountNo())
                ? TransactionAction.CREDIT
                : TransactionAction.DEBIT;

        //  Convert amount
        BigDecimal amount = new BigDecimal(webhookRequest.getAmount());

        // 3. Prevent duplicate transactions
        Optional<Transaction> existingTx =
                transactionRepository.findByTxRef(webhookRequest.getTrxnReferenceNo());

        if (existingTx.isPresent()) {
            log.warn("Duplicate webhook transaction ignored | trxnRef={}", webhookRequest.getTrxnReferenceNo());
            return responseUtil.success("Duplicate transaction ignored", existingTx.get());
        }

        // 7. Create and save transaction
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setTxRef(webhookRequest.getTrxnReferenceNo());
        transaction.setNarration(webhookRequest.getNarration());
        transaction.setAction(action);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setType(TransactionType.WALLET_DEPOSIT);
        transaction.setSource(webhookRequest.getSenderBankName());
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        transactionRepository.save(transaction);

        //  Update wallet balance
        double txnAmount = amount.doubleValue();

        if (action == TransactionAction.CREDIT) {
            user.setLast_balance(user.getLast_balance() + txnAmount);
        } else {
            user.setLast_balance(user.getLast_balance() - txnAmount);
        }

        userRepository.save(user);
        double updatedBalance = user.getLast_balance();

        //  Send email notification
        emailService.sendWalletWebhookNotification(user, transaction, updatedBalance);

        //  Send push notification (ONLY for CREDIT, non-blocking)
        if (action == TransactionAction.CREDIT) {
            fcmTokenRepository.findByUserId(user.getId())
                    .map(FCMToken::getToken)
                    .filter(token -> !token.isBlank())
                    .ifPresent(token -> {
                        try {
                            String title = "Money Received";
                            String body = String.format(
                                    "₦%,.2f has been credited to your wallet. New balance: ₦%,.2f",
                                    amount,
                                    updatedBalance
                            );

                            pushNotificationService.sendPush(
                                    token,
                                    title,
                                    body
                            );

                        } catch (Exception ex) {
                            // Push failure must NOT affect webhook processing
                            log.warn(
                                    "Push notification failed for user {}: {}",
                                    user.getId(),
                                    ex.getMessage()
                            );
                        }
                    });
        }

        log.info("Webhook transaction recorded | email={} | trxnRef={} | action={} | amount={} | newBalance={}", user.getEmailAddress(), webhookRequest.getTrxnReferenceNo(), action, amount, updatedBalance);
        //  Return success response
        return responseUtil.success("Wallet transaction recorded successfully",
                Map.of(
                        "transaction", transaction,
                        "newBalance", updatedBalance
                ));
    }
}
