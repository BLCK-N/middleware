package com.myrtle.middleware.services.payment;

import com.myrtle.middleware.dtos.TransactionDTO;
import com.myrtle.middleware.dtos.TransactionFilterRequest;
import com.myrtle.middleware.dtos.request.AddBankAccountRequest;
import com.myrtle.middleware.dtos.request.FunctionResponse;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.BankResponse;
import com.myrtle.middleware.dtos.response.StatsResponse;
import com.myrtle.middleware.enums.TransactionAction;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.GeneralAccount;
import com.myrtle.middleware.model.Transaction;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.GeneralAccountRepository;
import com.myrtle.middleware.repository.TransactionRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.investment.InvestmentService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.specification.TransactionSpecification;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ExternalAPIs externalAPIs;
    private final InvestmentService investmentService;
    private final GeneralAccountRepository generalAccountRepository;
    private final ResponseUtil responseUtil;

    public ResponseEntity<ApiResponse> getBanks() {
        log.debug("Fetching bank list");
        BankResponse bankResponse = externalAPIs.getBanks();
        if (bankResponse == null) {
            log.error("Bank list fetch returned null from external API");
            return responseUtil.conflict("Error Fetching Banks", null);
        }
        return responseUtil.success("Banks Fetched Successfully", bankResponse);
    }

    public ResponseEntity<ApiResponse> getStats(String email) {
        log.debug("Fetching stats | email={}", email);
        // 1. Find User
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        // 2. Fetch transactions by userId
        List<Transaction> transactions = transactionRepository.findByUserId(user.getId());

        double totalCredit = 0.0;
        double totalDebit = 0.0;
        double totalMain = 0.0;

        for (Transaction transaction : transactions) {
            double amount = transaction.getAmount().doubleValue();

            if (transaction.getAction().equals(TransactionAction.CREDIT)) {
                totalCredit += amount;
                totalMain += amount;
            } else if (transaction.getAction().equals(TransactionAction.DEBIT)) {
                totalDebit += amount;
                totalMain -= amount;
            }
        }

        // 3. Build stats DTO
        StatsResponse statsResponse = new StatsResponse();
        statsResponse.setTotalCredit(totalCredit);
        statsResponse.setTotalDebit(totalDebit);
        statsResponse.setTotalMain(totalMain);

        // 4. Get investments
        FunctionResponse investmentResponse =
                investmentService.getUserTotalInvestmentsByAllProducts(user.getEmailAddress());

        // 5. Combine into map
        Map<String, Object> combined = new HashMap<>();
        combined.put("investments", investmentResponse.data());
        combined.put("stats", statsResponse);

        // 6. Return wrapped response
        return responseUtil.success("Stats fetched successfully", combined);
    }

    public ResponseEntity<ApiResponse> fetchTransactions(String email, TransactionFilterRequest filter, Pageable pageable) {
        log.debug("Fetching transactions | email={} | page={} | size={}", email, pageable.getPageNumber(), pageable.getPageSize());
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        Page<Transaction> transactionPage = transactionRepository.findAll(
                TransactionSpecification.withFilters(filter, user.getId()),
                pageable
        );
        List<TransactionDTO> transactionDTOs = transactionPage.getContent()
                .stream()
                .map(this::mapToDTO)
                .sorted(Comparator.comparing(TransactionDTO::getCreatedAt).reversed())
                .toList();


        Map<String, Object> paginatedResponse = new HashMap<>();
        paginatedResponse.put("transactions", transactionDTOs);
        paginatedResponse.put("currentPage", transactionPage.getNumber());
        paginatedResponse.put("totalPages", transactionPage.getTotalPages());
        paginatedResponse.put("totalItems", transactionPage.getTotalElements());
        paginatedResponse.put("pageSize", transactionPage.getSize());

        return responseUtil.success("Transactions fetched successfully for user " + user.getEmailAddress(), paginatedResponse);
    }

    public ResponseEntity<ApiResponse> fetchTransactionById(String email, String id) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        Optional<Transaction> transactionOpt = transactionRepository.findById(id);

        if (transactionOpt.isEmpty()) {
            return responseUtil.badRequest("Transaction Not Found", null);
        }
        TransactionDTO transactionDTO = mapToDTO(transactionOpt.get());
        return responseUtil.success("Transaction fetched successfully", transactionDTO);
    }

    public ResponseEntity<ApiResponse> fetchGeneralAccounts() {
        List<GeneralAccount> generalAccounts = generalAccountRepository.findAll();
        return responseUtil.success("Settlement Accounts Fetched Successfully", generalAccounts);
    }

    public ResponseEntity<ApiResponse> addGeneralBankAccount(AddBankAccountRequest request) {
        log.info("Adding general bank account | accountNumber={} | productName={}", request.getAccountNumber(), request.getProductName());

        // 1 Validate request
        if (request.getAccountNumber() == null || request.getAccountNumber().isBlank()) {
            return responseUtil.badRequest("Account number is required", null);
        }

        // 2 Check for duplicate account
        boolean exists = generalAccountRepository
                .existsByAccountNumber(request.getAccountNumber());

        if (exists) {
            log.warn("Bank account already exists | accountNumber={}", request.getAccountNumber());
            return responseUtil.conflict("Bank account already exists", null);
        }

        // 3 Map verified bank details
        GeneralAccount account = new GeneralAccount();
        account.setAccountName(request.getAccountName());
        account.setAccountNumber(request.getAccountNumber());
        account.setBankName("UBA");
        account.setCurrency(request.getCurrency());
        account.setProductName(request.getProductName());
        account.setStatus("ACTIVE");

        generalAccountRepository.save(account);

        log.info("General bank account added | accountNumber={} | productName={}", account.getAccountNumber(), account.getProductName());
        // 4 Return success response
        return responseUtil.created("Bank account added successfully", account);
    }


    // Mapper (Entity → DTO)
    private TransactionDTO mapToDTO(Transaction tx) {
        return TransactionDTO.builder()
                .id(tx.getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .status(tx.getStatus())
                .action(tx.getAction())
                .narration(tx.getNarration())
                .txRef(tx.getTxRef())
                .source(tx.getSource())
                .userId(tx.getUser() != null ? tx.getUser().getId() : null)
                .investmentId(tx.getInvestment() != null ? tx.getInvestment().getId() : null)
                .build();
    }
}

