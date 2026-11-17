package com.myrtle.middleware.services.payment;

import com.myrtle.middleware.dtos.TxnCheckResponse;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.GeneralAccount;
import com.myrtle.middleware.model.ManualTransactionEntity;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.GeneralAccountRepository;
import com.myrtle.middleware.repository.ManualTransactionRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPaymentService {

    private final ResponseUtil responseUtil;
    private final UserRepository userRepository;
    private final GeneralAccountRepository generalAccountRepository;
    private final ManualTransactionRepository manualTransactionRepository;
    private final EmailService emailService;

    public ResponseEntity<ApiResponse> findDestinationAccount(String secId, UserDetails userDetails) {
        log.info("Finding destination account | secId={} | user={}", secId, userDetails.getUsername());
        User user = userRepository.findByEmailAddress(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        Optional<GeneralAccount> productAccount = generalAccountRepository.findByProductCodeIgnoreCase(secId);

        if (productAccount.isEmpty()) {
            throw new FunctionException("Invest product does not exist", null);
        }

        GeneralAccount product = productAccount.get();
        Map<String, Object> data = new HashMap<>();
        data.put("productId", product.getId());
        data.put("productName", product.getProductName());
        data.put("destinationBank", product.getBankName());
        data.put("destinationAccountName", product.getAccountName());
        data.put("destinationAccountNumber", product.getAccountNumber());
        data.put("txnReference", user.getPhoneNumber());

        return responseUtil.success("Destination account fetched successfully", data);
    }


    public ResponseEntity<ApiResponse> registerPayment(String productId, UserDetails userDetails) {
        log.info("Registering manual payment | productId={} | user={}", productId, userDetails.getUsername());
        User user = userRepository.findByEmailAddress(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        Optional<GeneralAccount> productAccount = generalAccountRepository.findById(productId);

        if (productAccount.isEmpty())
            throw new FunctionException("Investment product does not exist", null);

        ManualTransactionEntity txn = new ManualTransactionEntity();
        txn.setProductId(productId);
        txn.setUserId(user.getId());
        txn.setUserPhoneNumber(user.getPhoneNumber());
        txn.setTxnAmount(null);
        txn.setTxnStatus(com.myrtle.middleware.enums.TransactionStatus.PENDING);
        txn.setTxnTime(LocalDateTime.now());
        txn.setTransactionTreated(false);
        txn.setResolutionTryCount(0);
        txn.setCreatedAt(LocalDateTime.now());
        txn.setUpdatedAt(LocalDateTime.now());

        ManualTransactionEntity saved = manualTransactionRepository.save(txn);

        notifyTeamOfPaymentDeclaration(user, productAccount.get(), saved);

        log.info("Manual payment registered | transactionId={} | productId={} | user={} | status={}",
                saved.getId(), productId, user.getEmailAddress(), saved.getTxnStatus());
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", saved.getId());
        data.put("productId", saved.getProductId());
        data.put("status", saved.getTxnStatus().name());

        return responseUtil.created("Payment request registered successfully", data);
    }

    private void notifyTeamOfPaymentDeclaration(User user, GeneralAccount receivingAccount, ManualTransactionEntity txn) {
        try {
            emailService.sendInvestmentPaymentConfirmationReminder(user, receivingAccount, txn);
        } catch (Exception e) {
            log.error("Investment payment confirmation reminder failed | transactionId={} | user={}",
                    txn.getId(), user.getEmailAddress(), e);
        }
    }


    public ResponseEntity<ApiResponse> getManualTxnStatus(String transactionId, UserDetails userDetails) {
        User user = userRepository.findByEmailAddress(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        Optional<ManualTransactionEntity> latestOpt = manualTransactionRepository
                .findById(transactionId);

        if (latestOpt.isEmpty()) {
            log.warn("Manual transaction status check failed: not found | transactionId={} | user={}", transactionId, user.getEmailAddress());
            return responseUtil.notFound("Transaction does not exist.", null);
        }

        ManualTransactionEntity txn = latestOpt.get();
        TxnCheckResponse txnResponse = new TxnCheckResponse();
        txnResponse.setTxnReference(txn.getUserPhoneNumber());
        txnResponse.setTransactionId(txn.getId());
        txnResponse.setProductId(txn.getProductId());
        txnResponse.setStatus(txn.getTxnStatus());
        txnResponse.setTxnTime(txn.getTxnTime());
        txnResponse.setTransactionTreated(txn.getTransactionTreated());
        txnResponse.setInvestmentBooked(txn.getInvestmentBooked());
        txnResponse.setTxnAmount(txn.getTxnAmount());
        return responseUtil.success("Transaction status fetched successfully", txnResponse);
    }

    public ResponseEntity<ApiResponse> getTransactionsByUserId(UserDetails userDetails, Pageable pageable) {
        User user = userRepository.findByEmailAddress(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        Page<ManualTransactionEntity> transactionPage = manualTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        List<TxnCheckResponse> transactions = transactionPage.getContent().stream()
                .map(this::mapToTxnCheckResponse)
                .collect(Collectors.toList());

        Map<String, Object> paginatedResponse = new HashMap<>();
        paginatedResponse.put("transactions", transactions);
        paginatedResponse.put("currentPage", transactionPage.getNumber());
        paginatedResponse.put("totalPages", transactionPage.getTotalPages());
        paginatedResponse.put("totalItems", transactionPage.getTotalElements());
        paginatedResponse.put("pageSize", transactionPage.getSize());

        return responseUtil.success("Manual transactions fetched successfully", paginatedResponse);
    }

    private TxnCheckResponse mapToTxnCheckResponse(ManualTransactionEntity txn) {
        TxnCheckResponse response = new TxnCheckResponse();
        response.setTransactionId(txn.getId());
        response.setProductId(txn.getProductId());
        response.setTxnAmount(txn.getTxnAmount());
        response.setStatus(txn.getTxnStatus());
        response.setTxnTime(txn.getTxnTime());
        response.setTransactionTreated(txn.getTransactionTreated());
        response.setTxnReference(txn.getUserPhoneNumber());
        response.setInvestmentBooked(txn.getInvestmentBooked());
        return response;
    }
}
