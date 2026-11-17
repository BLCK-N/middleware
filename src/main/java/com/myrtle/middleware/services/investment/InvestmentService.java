package com.myrtle.middleware.services.investment;

import com.myrtle.middleware.dtos.InvestmentDTO;
import com.myrtle.middleware.dtos.InvestmentFilterRequest;
import com.myrtle.middleware.dtos.WithdrawalDTO;
import com.myrtle.middleware.dtos.request.*;
import com.myrtle.middleware.dtos.response.*;
import com.myrtle.middleware.enums.Currency;
import com.myrtle.middleware.enums.TransactionAction;
import com.myrtle.middleware.enums.TransactionStatus;
import com.myrtle.middleware.enums.TransactionType;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.*;
import com.myrtle.middleware.repository.*;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.corporate.CorporateUserPolicy;
import com.myrtle.middleware.services.payment.FlutterwavePaymentService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.Generators;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentService {

    private static final String LEDGER_MARKET_CODE = "FUNDS";
    private static final String LEDGER_START_DATE = "2021-01-01";
    private static final String SUPPORT_EMAIL = "wecare@myrtleng.com";
    private final ExternalAPIs externalAPIs;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final InvestmentRepository investmentRepository;
    private final TransactionRepository transactionRepository;
    private final WithdrawalHistoryRepository withdrawalHistoryRepository;
    private final ProductRespository productRespository;
    private final FlutterwavePaymentService flutterwavePaymentService;
    private final PendingPaymentRepository pendingPaymentRepository;
    private final GeneralAccountRepository generalAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final ResponseUtil responseUtil;
    private final WithdrawalAccountRespository withdrawalAccountRespository;
    private final CorporateUserPolicy corporateUserPolicy;
    @Value("${app.host}")
    private String appHost;

    private static BigDecimal parseAndValidateAmount(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            throw new FunctionException("Termination amount is required", null);
        }
        try {
            BigDecimal value = new BigDecimal(amount.trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new FunctionException("Termination amount must be greater than zero", null);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new FunctionException("Invalid termination amount format", null);
        }
    }

    private static void validateNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new FunctionException(message, null);
        }
    }


    private ResponseEntity<ApiResponse> response(HttpStatus status, String message, Object data) {
        return switch (status) {
            case OK -> responseUtil.success(message, data);
            case CREATED -> responseUtil.created(message, data);
            case BAD_REQUEST -> responseUtil.badRequest(message, data);
            case NOT_FOUND -> responseUtil.notFound(message, data);
            case UNAUTHORIZED -> responseUtil.unauthorized(message, data);
            case CONFLICT -> responseUtil.conflict(message, data);
            case INTERNAL_SERVER_ERROR -> responseUtil.serverError(message, data);
            case SERVICE_UNAVAILABLE -> responseUtil.serviceUnavailable(message, data);
            default -> responseUtil.serverError(message, data);
        };
    }

    private ResponseEntity<ApiResponse> fromFR(FunctionResponse fr) {
        if (fr == null) return responseUtil.serverError("An error occurred", null);
        return response(fr.status(), fr.message(), fr.data());
    }

    private FunctionResponse validateInvestmentLimits(User user, Product product, BigDecimal investmentAmount) {
        if (user == null || product == null || investmentAmount == null) {
            throw new FunctionException("An unexpected error occurred. Please try again later.", null);
        }

        if ((product.getInitialLimit() == null || product.getInitialLimit().isBlank()) &&
                (product.getMinimumLimit() == null || product.getMinimumLimit().isBlank())) {
            return null;
        }

        try {
            List<Investment> existingInvestments = investmentRepository.findAll().stream()
                    .filter(inv -> inv.getUser().getId().equals(user.getId()) &&
                            inv.getProduct().getId().equals(product.getId()))
                    .toList();

            boolean userHasExistingInvestment = !existingInvestments.isEmpty();

            if (!userHasExistingInvestment && product.getInitialLimit() != null && !product.getInitialLimit().isBlank()) {
                try {
                    BigDecimal initialLimit = new BigDecimal(product.getInitialLimit());
                    if (investmentAmount.compareTo(initialLimit) < 0) {
                        throw new FunctionException(String.format("Initial investment must be at least ₦%s. Your current amount is ₦%s. Please increase your investment amount.",
                                product.getInitialLimit(), investmentAmount), null);
                    }
                } catch (NumberFormatException e) {
                    throw new FunctionException("Invalid initial limit configuration. Please contact support.", null);
                }
            } else if (userHasExistingInvestment && product.getMinimumLimit() != null && !product.getMinimumLimit().isBlank()) {
                try {
                    BigDecimal minimumLimit = new BigDecimal(product.getMinimumLimit());
                    if (investmentAmount.compareTo(minimumLimit) < 0) {
                        throw new FunctionException(String.format("Minimum investment amount is ₦%s. Your current amount is ₦%s. Please increase your investment amount.",
                                product.getMinimumLimit(), investmentAmount), null);
                    }
                } catch (NumberFormatException e) {
                    throw new FunctionException("Invalid minimum limit configuration. Please contact support.", null);
                }
            }
        } catch (Exception e) {
            throw new FunctionException("An error occurred while validating your investment. Please try again.", null);
        }

        return null;
    }

    @Transactional
    public ResponseEntity<ApiResponse> createInvestment(String email, InvestmentPayload payload) {
        log.info("Creating investment | email={} | productId={}", email, payload.getProductId());

        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found"));
        String txnRef = user.getPhoneNumber() + "-" + System.currentTimeMillis();

        boolean isValid = passwordEncoder.matches(payload.getPin(), user.getPin());
        if (!isValid) {
            log.warn("Investment creation failed: incorrect PIN | email={}", email);
            return response(HttpStatus.BAD_REQUEST, "Incorrect PIN", null);
        }

        Product product = productRespository.findById(payload.getProductId())
                .orElseThrow(() -> new ResourceNotFound("Product not found"));

        InvestmentOrderRequest request = payload.getRequest();
        if (request == null) {
            return response(HttpStatus.BAD_REQUEST, "Investment request is required", null);
        }

        if (request.getOrderType() == null || request.getCurrency() == null || request.getChannel() == null) {
            return response(HttpStatus.BAD_REQUEST, "Invalid investment request: missing required enum fields", null);
        }

        PendingPayment pendingPayment = new PendingPayment();
        pendingPayment.setUser(user);
        pendingPayment.setProduct(product);
        pendingPayment.setTransRef(txnRef);
        pendingPayment.setAccountId(request.getAccountId());
        pendingPayment.setOrderType(PendingPayment.OrderType.valueOf(request.getOrderType().name()));
        pendingPayment.setMarketCode(request.getMarketCode());
        pendingPayment.setSecId(request.getSecId());
        pendingPayment.setRequestedQty(request.getRequestedQty());
        pendingPayment.setRequestedVal(request.getRequestedVal());
        pendingPayment.setPrice(request.getPrice());
        pendingPayment.setRequestDate(LocalDate.parse(request.getRequestDate()).atStartOfDay());
        pendingPayment.setCurrency(PendingPayment.Currency.valueOf(request.getCurrency().name()));
        pendingPayment.setNotes(request.getNotes());
        pendingPayment.setAutoApprove(Boolean.TRUE.equals(request.getAutoApprove()));
        pendingPayment.setAutoPayRedemption(Boolean.TRUE.equals(request.getAutoPayRedemption()));
        pendingPayment.setChannel(PendingPayment.Channel.valueOf(request.getChannel().name()));
        pendingPayment.setRefCode(request.getRefCode());
        pendingPayment.setTxRef(txnRef);
        String amount = String.valueOf(Double.parseDouble(request.getRequestedQty()) * Double.parseDouble(request.getPrice()));
        pendingPayment.setAmount(
                amount
        );
        pendingPayment.setPaymentStatus(PendingPayment.PaymentStatus.PENDING);
        pendingPayment.setCreatedAt(LocalDateTime.now());


        BigDecimal investmentAmount = new BigDecimal(amount);
        validateInvestmentLimits(user, product, investmentAmount);

        pendingPaymentRepository.save(pendingPayment);

        Optional<GeneralAccount> generalAccount = generalAccountRepository.findByProductCodeIgnoreCase(product.getCode());

        if (generalAccount.isEmpty()) {
            return response(HttpStatus.NOT_FOUND, "Settlement Account Not Found", null);
        }

        try {
            String paymentLink = flutterwavePaymentService.createPaymentLink(
                    product.getProductType(), email, amount, txnRef, generalAccount.get().getCurrency());

            log.info("Payment link created | email={} | txnRef={} | amount={}", email, txnRef, amount);
            return response(HttpStatus.OK, "Payment link generated successfully", paymentLink);
        } catch (Exception ex) {
            log.error("Payment link creation failed | email={} | txnRef={} | reason={}", email, txnRef, ex.getMessage(), ex);
            return response(HttpStatus.BAD_REQUEST, "We couldn't create your payment link. Please try again.", null);
        }
    }

    @Transactional
    public FunctionResponse processPendingInvestmentPayment(String txRef) {
        log.info("Processing pending investment payment | txRef={}", txRef);

        PendingPayment pendingPayment = pendingPaymentRepository.findByTxRef(txRef)
                .orElseThrow(() -> new ResourceNotFound("Pending payment not found"));

        if (pendingPayment.getPaymentStatus() != PendingPayment.PaymentStatus.PENDING) {
            log.warn("Payment already processed | txRef={} | status={}", txRef, pendingPayment.getPaymentStatus());
            throw new FunctionException("Payment already processed", null);
        }

        User user = pendingPayment.getUser();
        Product product = pendingPayment.getProduct();

        InvestmentOrderRequest request = new InvestmentOrderRequest();
        request.setAccountId(pendingPayment.getAccountId());
        request.setOrderType(InvestmentOrderRequest.OrderType.valueOf(pendingPayment.getOrderType().name()));
        request.setMarketCode(pendingPayment.getMarketCode());
        request.setSecId(pendingPayment.getSecId());
        request.setRequestedQty(pendingPayment.getPrice());
        request.setRequestedVal(pendingPayment.getRequestedVal());
        request.setPrice(pendingPayment.getRequestedQty());
        request.setRequestDate(pendingPayment.getRequestDate().toLocalDate().toString());
        request.setCurrency(InvestmentOrderRequest.Currency.valueOf(pendingPayment.getCurrency().name()));
        request.setNotes(pendingPayment.getNotes());
        request.setAutoApprove(pendingPayment.getAutoApprove());
        request.setAutoPayRedemption(pendingPayment.getAutoPayRedemption());
        request.setChannel(InvestmentOrderRequest.Channel.valueOf(pendingPayment.getChannel().name()));
        request.setRefCode(pendingPayment.getRefCode());

        BigDecimal amount = new BigDecimal(pendingPayment.getAmount());

        String valueDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        TransactionRequest transactionRequest = new TransactionRequest(
                user.getInvestmentAccountId(),
                user.getBranchId(),
                user.getInvestmentAccountId(),
                TransactionRequest.DestAccountType.DEPOSIT,
                TransactionRequest.Channel.CASH,
                TransactionRequest.TransactionType.DEPOSIT,
                amount.doubleValue(),
                TransactionRequest.CurrencyCode.NGN,
                Generators.generateReference(),
                request.getNotes(),
                valueDate,
                true,
                "",
                Generators.generateRefCode(),
                true
        );

        TransactionResponse response = externalAPIs.createCashTransaction(transactionRequest);
        if (response == null) {
            log.error("Cash transaction creation returned null | txRef={}", txRef);
            throw new IllegalStateException("Failed to create cash transaction");
        }
        log.debug("Cash transaction created | txRef={} | transactionId={}", txRef, response.getId());

        PostTransactionResponse postResponse = externalAPIs.postTransaction(response.getId());
        if (postResponse == null) {
            log.error("Post transaction returned null | txRef={} | transactionId={}", txRef, response.getId());
            throw new IllegalStateException("Failed to post cash transaction");
        }

        FundTransactionResponse fundResponse = externalAPIs.createTransaction(request, null);
        if (fundResponse == null || fundResponse.getId() == null) {
            log.error("Investment transaction creation returned null | txRef={}", txRef);
            throw new IllegalStateException("Failed to create investment transaction");
        }
        log.debug("Investment transaction created | txRef={} | fundTransactionId={}", txRef, fundResponse.getId());

        Investment investment = new Investment();
        investment.setUser(user);
        investment.setProduct(product);
        investment.setAccountId(request.getAccountId());
        investment.setOrderType(Investment.OrderType.valueOf(request.getOrderType().name()));
        investment.setMarketCode(request.getMarketCode());
        investment.setSecId(request.getSecId());
        investment.setRequestedQty(
                String.valueOf(amount)
        );
        investment.setRequestedVal(request.getRequestedVal());
        investment.setPrice("1");
        investment.setRequestDate(LocalDateTime.parse(request.getRequestDate() + "T00:00:00"));
        investment.setCurrency(Investment.Currency.valueOf(request.getCurrency().name()));
        investment.setNotes(request.getNotes());
        investment.setAutoApprove(request.getAutoApprove());
        investment.setAutoPayRedemption(request.getAutoPayRedemption());
        investment.setChannel(Investment.Channel.valueOf(request.getChannel().name()));
        investment.setRefCode(request.getRefCode());
        investment.setCreatedAt(LocalDateTime.now());

        investmentRepository.save(investment);

        Transaction transaction = new Transaction();
        transaction.setType(
                request.getOrderType() == InvestmentOrderRequest.OrderType.SUBSCRIPTION
                        ? TransactionType.INVESTMENT_CREATION
                        : TransactionType.WALLET_WITHDRAWAL
        );
        transaction.setAmount(amount);
        transaction.setTxRef(txRef);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setUser(user);
        transaction.setInvestment(investment);
        transaction.setAction(TransactionAction.CREDIT);
        transaction.setNarration("Investment created after payment confirmation");
        transaction.setSource("Investment in " + product.getLabel());

        transactionRepository.save(transaction);

        pendingPayment.setPaymentStatus(PendingPayment.PaymentStatus.SUCCESS);
        pendingPayment.setCompletedAt(LocalDateTime.now());
        pendingPaymentRepository.save(pendingPayment);

        log.info("Investment processed successfully | txRef={} | investmentId={} | amount={}", txRef, investment.getId(), pendingPayment.getAmount());
        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Investment processed successfully")
                .data(Map.of(
                        "investmentId", investment.getId(),
                        "channel", pendingPayment.getChannel().name(),
                        "productType", pendingPayment.getProduct().getProductType(),
                        "amount", pendingPayment.getAmount()
                ))
                .build();
    }

    @Transactional
    public FunctionResponse createInvestmentWithWallet(String email, InvestmentPayload payload) {
        log.info("Creating investment via wallet | email={} | productId={}", email, payload.getProductId());

        InvestmentOrderRequest request = payload.getRequest();
        String refCode = request.getRefCode();

        if (refCode != null && !refCode.isBlank()) {
            Optional<Investment> existingInvestment = investmentRepository.findByRefCode(refCode);
            if (existingInvestment.isPresent()) {
                throw new FunctionException("Duplicate investment detected. Investment with refCode '" + refCode + "' already exists.", null);
            }
        }

        String trxnRefNo = Generators.generateTrxnRef("000002");

        Optional<Transaction> existingTransaction = transactionRepository.findByTxRef(trxnRefNo);
        if (existingTransaction.isPresent()) {
            throw new FunctionException("Duplicate transaction detected. Transaction with reference '" + trxnRefNo + "' already exists.", null);
        }

        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        boolean isValid = passwordEncoder.matches(payload.getPin(), user.getPin());
        if (!isValid) {
            throw new FunctionException("Incorrect PIN", null);
        }

        GeminiBalanceResponse balanceResponse =
                externalAPIs.getBalance(user.getAccountNumber());

        if (balanceResponse == null || !"00".equals(balanceResponse.getResponseCode())) {
            throw new FunctionException("Unable to retrieve wallet balance", balanceResponse);
        }

        BigDecimal investmentAmount = BigDecimal.valueOf(
                Double.parseDouble(request.getPrice()) *
                        Double.parseDouble(request.getRequestedQty())
        );

        TransactionRequest.CurrencyCode currencyCode =
                TransactionRequest.CurrencyCode.valueOf(
                        payload.getRequest().getCurrency().name()
                );

        BigDecimal lastBalance = BigDecimal.valueOf(user.getLast_balance());

        BigDecimal availableBalance = new BigDecimal(balanceResponse.getBalance()).subtract(lastBalance);

        if (new BigDecimal(balanceResponse.getBalance()).compareTo(investmentAmount) < 0) {
            throw new FunctionException("Insufficient wallet balance after considering previous investments", balanceResponse);
        }

        Product product = productRespository.findById(payload.getProductId())
                .orElseThrow(() -> new ResourceNotFound("Product not Found"));

        validateInvestmentLimits(user, product, investmentAmount);

        GeneralAccount settlementAccount =
                generalAccountRepository.findByProductCodeIgnoreCase(product.getCode())
                        .orElseThrow(() ->
                                new ResourceNotFound(
                                        "Settlement account not configured for product"
                                )
                        );

        String valueDate = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        TransactionRequest transactionRequest = new TransactionRequest(
                user.getInvestmentAccountId(),
                user.getBranchId(),
                user.getInvestmentAccountId(),
                TransactionRequest.DestAccountType.DEPOSIT,
                TransactionRequest.Channel.CASH,
                TransactionRequest.TransactionType.DEPOSIT,
                investmentAmount.doubleValue(),
                currencyCode,
                Generators.generateReference(),
                request.getNotes(),
                valueDate,
                true,
                "",
                Generators.generateRefCode(),
                true
        );

        transactionRequest.setAccountId(user.getInvestmentAccountId());
        transactionRequest.setDestAccountId(user.getInvestmentAccountId());

        TransactionResponse transactionResponse =
                externalAPIs.createCashTransaction(transactionRequest);

        log.debug("Cash transaction response | email={} | response={}", email, transactionResponse);
        if (transactionResponse == null) {
            log.error("Cash transaction creation failed | email={} | amount={}", email, investmentAmount);
            throw new FunctionException("Error creating wallet debit transaction", null);
        }

        PostTransactionResponse postTransactionResponse =
                externalAPIs.postTransaction(transactionResponse.getId());

        log.debug("Post transaction response | email={} | transactionId={} | response={}", email, transactionResponse.getId(), postTransactionResponse);
        if (postTransactionResponse == null) {
            log.error("Post transaction failed | email={} | transactionId={}", email, transactionResponse.getId());
            throw new FunctionException("Error posting wallet transaction", null);
        }

        request.setAccountId(user.getInvestmentAccountId());

        FundTransactionResponse fundTransactionResponse =
                externalAPIs.createTransaction(request, null);

        if (fundTransactionResponse == null || fundTransactionResponse.getId() == null) {
            throw new FunctionException("Error creating investment", null);
        }

        BankAccountRequest bankAccountRequest = new BankAccountRequest();
        bankAccountRequest.setBankCode("000004");
        bankAccountRequest.setAccountNo(settlementAccount.getAccountNumber());
        BankAccountResponse bankAccountResponse = externalAPIs.nameInquiry(bankAccountRequest);

        if (bankAccountResponse == null || bankAccountResponse.getAccountName() == null) {
            throw new FunctionException("Error Creating Investment with External API", null);
        }

        BankTransferRequest bankTransferRequest = new BankTransferRequest();
        bankTransferRequest.setAccountNo(user.getAccountNumber());
        bankTransferRequest.setAmount(investmentAmount.toPlainString());
        bankTransferRequest.setBeneficiaryAccountName(
                settlementAccount.getAccountName()
        );
        bankTransferRequest.setBeneficiaryAccountNumber(
                settlementAccount.getAccountNumber()
        );
        bankTransferRequest.setNarration(
                "Withdrawal of " + investmentAmount + " for investment"
        );
        bankTransferRequest.setTrxnFee("0");
        bankTransferRequest.setMerchantCode("MYTLE");
        bankTransferRequest.setNameEnquiryRef(bankAccountResponse.getSessionId());
        bankTransferRequest.setTrxnReferenceNo(trxnRefNo);
        bankTransferRequest.setDestinationInstitutionCode("000004");
        bankTransferRequest.setBeneficiaryBankVerificationNumber("");

        BankTransferResponse bankTransferResponse =
                externalAPIs.bankTransfer(bankTransferRequest);

        log.debug("Bank transfer response | email={} | response={}", email, bankTransferResponse);
        if (bankTransferResponse == null ||
                !"00".equals(bankTransferResponse.getResponseCode())) {
            log.error("Bank transfer failed | email={} | responseCode={}", email,
                    bankTransferResponse != null ? bankTransferResponse.getResponseCode() : "null");
            throw new FunctionException("Bank transfer failed", null);
        }
        log.info("Bank transfer successful | email={} | trxnRefNo={} | amount={}", email, bankTransferResponse.getTrxnRefNo(), investmentAmount);

        Transaction withdrawalTransaction = new Transaction();
        withdrawalTransaction.setType(TransactionType.WALLET_WITHDRAWAL);
        withdrawalTransaction.setAmount(investmentAmount);
        withdrawalTransaction.setTxRef(bankTransferResponse.getTrxnRefNo());
        withdrawalTransaction.setCreatedAt(LocalDateTime.now());
        withdrawalTransaction.setStatus(TransactionStatus.SUCCESS);
        withdrawalTransaction.setUser(user);
        withdrawalTransaction.setAction(TransactionAction.DEBIT);
        withdrawalTransaction.setNarration("Wallet withdrawal for investment funding");
        withdrawalTransaction.setSource("Wallet");

        transactionRepository.save(withdrawalTransaction);

        Investment investment = new Investment();
        investment.setUser(user);
        investment.setProduct(product);
        investment.setCreatedAt(LocalDateTime.now());
        investment.setAccountId(request.getAccountId());
        investment.setOrderType(
                Investment.OrderType.valueOf(request.getOrderType().name())
        );
        investment.setMarketCode(request.getMarketCode());
        investment.setSecId(request.getSecId());
        investment.setRequestedQty(
                String.valueOf(investmentAmount)
        );
        investment.setRequestedVal(request.getRequestedVal());
        investment.setPrice("1");
        investment.setRequestDate(
                LocalDateTime.parse(request.getRequestDate() + "T00:00:00")
        );
        investment.setCurrency(
                Investment.Currency.valueOf(request.getCurrency().name())
        );
        investment.setNotes(request.getNotes());
        investment.setAutoApprove(request.getAutoApprove());
        investment.setAutoPayRedemption(request.getAutoPayRedemption());
        investment.setChannel(
                Investment.Channel.valueOf(request.getChannel().name())
        );
        investment.setInvestmentStatus(Investment.InvestmentStatus.RUNNING);
        investment.setRefCode(request.getRefCode());

        investment = investmentRepository.save(investment);
        log.info("Investment created via wallet | email={} | investmentId={} | amount={}", email, investment.getId(), investmentAmount);

        Transaction investmentTransaction = new Transaction();
        investmentTransaction.setType(TransactionType.INVESTMENT_CREATION);
        investmentTransaction.setAmount(investmentAmount);
        investmentTransaction.setTxRef(bankTransferResponse.getTrxnRefNo());
        investmentTransaction.setCreatedAt(LocalDateTime.now());
        investmentTransaction.setStatus(TransactionStatus.SUCCESS);
        investmentTransaction.setUser(user);
        investmentTransaction.setInvestment(investment);
        investmentTransaction.setAction(TransactionAction.CREDIT);
        investmentTransaction.setNarration("Investment created via wallet");
        investmentTransaction.setSource("Investment - " + product.getLabel());

        transactionRepository.save(investmentTransaction);

        return new FunctionResponse(
                true,
                HttpStatus.OK,
                "Investment made successfully",
                null
        );
    }

    public FunctionResponse fetchAllRunningInvestments(String email, int page, int size) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Investment> investmentPage = investmentRepository.findByUserId(user.getId(), pageable);

        List<InvestmentDTO> investmentDTOs = investmentPage.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("content", investmentDTOs);
        responseData.put("currentPage", investmentPage.getNumber());
        responseData.put("totalItems", investmentPage.getTotalElements());
        responseData.put("totalPages", investmentPage.getTotalPages());

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Investments fetched successfully")
                .data(responseData)
                .build();
    }

    public FunctionResponse fetchAllRunningInvestmentsByProduct(String email, String productId, int page, int size) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Investment> investmentPage = investmentRepository.findByProduct_IdAndUser_Id(productId, user.getId(), pageable);

        List<Investment> activeInvestments = investmentRepository
                .findByUser_IdAndProduct_IdAndInvestmentStatus(
                        user.getId(),
                        productId,
                        Investment.InvestmentStatus.RUNNING
                );
        boolean allTerminated = activeInvestments.isEmpty();


        List<InvestmentDTO> investmentDTOs = investmentPage.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("allTerminated", allTerminated);
        responseData.put("content", investmentDTOs);
        responseData.put("currentPage", investmentPage.getNumber());
        responseData.put("totalItems", investmentPage.getTotalElements());
        responseData.put("totalPages", investmentPage.getTotalPages());

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Investments fetched successfully")
                .data(responseData)
                .build();
    }

    public FunctionResponse getUserTotalInvestmentsByAllProducts(String email) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        List<Product> products = productRespository.findAll();

        List<Map<String, Object>> productTotals = products.stream().map(product -> {
            List<Investment> investments = investmentRepository.findByProduct_IdAndUser_Id(product.getId(), user.getId(), null).getContent();

            BigDecimal investmentBalance = investments.stream()
                    .filter(inv -> inv.getInvestmentStatus().equals(Investment.InvestmentStatus.RUNNING))
                    .map(inv -> BigDecimal.valueOf(Double.parseDouble(inv.getPrice()))
                            .multiply(BigDecimal.valueOf(Double.parseDouble(inv.getRequestedQty()))))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> productData = new HashMap<>();
            productData.put("productName", product.getLabel());
            productData.put("productId", product.getId());
            productData.put("totalAmountInvested", investmentBalance);

            return productData;
        }).collect(Collectors.toList());

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Total investments calculated successfully")
                .data(productTotals)
                .build();
    }

    public ResponseEntity<?> getUserTotalInvestmentsStatsByAllProducts(String email) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        if (user.getClientId() == null || user.getClientId().isBlank()) {
            throw new FunctionException("Client ID is missing on user profile. Please complete onboarding.", null);
        }

        List<Product> products = productRespository.findAll();

        ClientPortfolioResponse response = externalAPIs.clientPortfolioResponse(user.getClientId(), Currency.NGN);

        Map<String, BigDecimal> balancesBySecId = new HashMap<>();

        if (response != null && response.getInvestmentAccounts() != null) {
            response.getInvestmentAccounts().stream()
                    .filter(Objects::nonNull)
                    .map(ClientPortfolioResponse.InvestmentAccount::getPositions)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .forEach(position -> {
                        String secId = position.getSecId();
                        if (secId == null || secId.isBlank()) {
                            return;
                        }

                        BigDecimal positionBalance = position.getReportValue();
                        if (positionBalance == null) {
                            positionBalance = position.getPositionValue();
                        }
                        if (positionBalance == null
                                && position.getPrice() != null
                                && position.getQuantity() != null) {
                            positionBalance = position.getPrice().multiply(position.getQuantity());
                        }

                        balancesBySecId.merge(secId, positionBalance == null ? BigDecimal.ZERO : positionBalance, BigDecimal::add);
                    });
        }

        List<Map<String, Object>> productTotals = products.stream().map(product -> {
            BigDecimal investmentBalance = balancesBySecId.getOrDefault(product.getCode(), BigDecimal.ZERO);

            Map<String, Object> productData = new LinkedHashMap<>();
            productData.put("productName", product.getLabel());
            productData.put("secId", product.getCode());
            productData.put("productId", product.getId());
            productData.put("totalAmountInvested", investmentBalance);
            productData.put("currency", product.getCurrency());
            return productData;
        }).collect(Collectors.toList());

        BigDecimal totalBalance = productTotals.stream()
                .map(item -> (BigDecimal) item.get("totalAmountInvested"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FunctionResponseForStats functionResponse = FunctionResponseForStats.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Total investments calculated successfully")
                .data(productTotals)
                .totalBalance(totalBalance.toPlainString())
                .build();

        return new ResponseEntity<>(functionResponse, functionResponse.status());
    }

    public FunctionResponse getInitialLimitStatusForAllProducts(String email) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        List<Product> products = productRespository.findAll();
        List<ProductInitialLimitStatus> statuses = new ArrayList<>();

        for (Product product : products) {
            boolean passed = false;

            // If no initial limit configured, mark as passed
            if (product.getInitialLimit() == null || product.getInitialLimit().isBlank()) {
                passed = true;
            } else {
                try {
                    BigDecimal initialLimit = new BigDecimal(product.getInitialLimit());

                    // Check if user has any existing investments in this product
                    List<Investment> existingInvestments = investmentRepository.findAll().stream()
                            .filter(inv -> inv.getUser().getId().equals(user.getId()) &&
                                    inv.getProduct().getId().equals(product.getId()))
                            .toList();

                    // Check if any individual investment meets or exceeds the initial limit
                    for (Investment investment : existingInvestments) {
                        BigDecimal investmentAmount = new BigDecimal(investment.getPrice())
                                .multiply(new BigDecimal(investment.getRequestedQty()));

                        if (investmentAmount.compareTo(initialLimit) >= 0) {
                            passed = true;
                            break;
                        }
                    }

                } catch (NumberFormatException e) {
                    passed = false;
                } catch (Exception e) {
                    passed = false;
                }
            }

            statuses.add(new ProductInitialLimitStatus(product.getLabel(), passed));
        }

        return new FunctionResponse(true, HttpStatus.OK, "Initial limit status fetched", statuses);
    }

    public FunctionResponse getTotalInvestmentAmount(String email) {

        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Try external portfolio API first
        ClientPortfolioResponse clientPortfolioResponse = externalAPIs.clientPortfolioResponse(user.getClientId(), Currency.NGN);

        if (clientPortfolioResponse != null
                && clientPortfolioResponse.getInvestmentAccounts() != null
                && !clientPortfolioResponse.getInvestmentAccounts().isEmpty()) {

            totalAmount = clientPortfolioResponse.getCurrentAccountValue();

        } else {
            // Fallback to local database calculation
            List<Investment> investments =
                    investmentRepository.findByUserId(user.getId(), null).getContent();

            totalAmount = investments.stream()
                    .filter(inv -> inv.getInvestmentStatus().equals(Investment.InvestmentStatus.RUNNING))
                    .map(inv ->
                            new BigDecimal(inv.getPrice())
                                    .multiply(new BigDecimal(inv.getRequestedQty()))
                    )
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

//        GeminiBalanceResponse balanceResponse = externalAPIs.getBalance(user.getAccountNumber());

//        if (balanceResponse == null || !"00".equals(balanceResponse.getResponseCode())) {
//            throw new FunctionException("Error occurred while retrieving account balance", balanceResponse);
//        }
//
//        user.setLast_balance(balanceResponse.getBalance());
//        userRepository.save(user);

        BigDecimal wallet_balance = BigDecimal.ZERO;
        BigDecimal totalBalance = totalAmount.add(wallet_balance);

        Map<String, Object> result = new HashMap<>();
        result.put("asset_balance", totalAmount.setScale(2, RoundingMode.HALF_DOWN));
        result.put("wallet_balance", wallet_balance.setScale(2, RoundingMode.HALF_DOWN));
        result.put("total_balance", totalBalance.setScale(2, RoundingMode.HALF_DOWN));

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Total investments calculated successfully")
                .data(result)
                .build();
    }

    public FunctionResponse getChartData(String email) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        if (user.getClientId() == null || user.getClientId().isBlank()) {
            throw new FunctionException("Client ID is missing on user profile. Please complete onboarding.", null);
        }

        try {
            ClientReportResponse response = externalAPIs.getClientValuationReport(user.getClientId());

            return FunctionResponse.builder()
                    .isSuccess(true)
                    .status(HttpStatus.OK)
                    .message("Total investments calculated successfully")
                    .data(response)
                    .build();
        } catch (Exception ex) {
            throw new FunctionException("Unable to fetch client valuation report at this time", Map.of("error", ex.getMessage()));
        }
    }

    public FunctionResponse getRates(String email, LedgerStatementRequest request) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        if (request == null
                || request.getMarketCode() == null || request.getMarketCode().isBlank()
                || request.getSecId() == null || request.getSecId().isBlank()
                || request.getStartDate() == null || request.getStartDate().isBlank()
                || request.getEndDate() == null || request.getEndDate().isBlank()) {
            throw new FunctionException("Missing required parameters: marketCode, secId, startDate, endDate", null);
        }

        // Prefer caller-provided accountId when supplied, else fallback to user's investment account
        String resolvedAccountId = user.getInvestmentAccountId();

        if (resolvedAccountId == null || resolvedAccountId.isBlank()) {
            throw new FunctionException("Investment account is missing. Provide accountId in request or ensure user has an investmentAccountId.", null);
        }

        LedgerStatementRequest ledgerStatementRequest = new LedgerStatementRequest();
        ledgerStatementRequest.setAccountId(resolvedAccountId);
        ledgerStatementRequest.setMarketCode(request.getMarketCode());
        ledgerStatementRequest.setSecId(request.getSecId());
        ledgerStatementRequest.setStartDate("2021-01-01");
        ledgerStatementRequest.setEndDate(String.valueOf(LocalDate.now()));

        try {
            LedgerStatementResponse response = externalAPIs.getRateStats(ledgerStatementRequest);

            return FunctionResponse.builder()
                    .isSuccess(true)
                    .status(HttpStatus.OK)
                    .message("Investment Rates Fetched Successfully")
                    .data(response)
                    .build();
        } catch (Exception ex) {
            throw new FunctionException("Unable to fetch investment rates at this time", Map.of(
                    "accountId", ledgerStatementRequest.getAccountId(),
                    "marketCode", ledgerStatementRequest.getMarketCode(),
                    "secId", ledgerStatementRequest.getSecId(),
                    "startDate", ledgerStatementRequest.getStartDate(),
                    "endDate", ledgerStatementRequest.getEndDate(),
                    "error", ex.getMessage()
            ));
        }
    }

    public FunctionResponse terminateInvestment(String email, String investmentId, String pin, String amount) {
        log.info("Investment termination requested | email={} | investmentId={} | amount={}", email, investmentId, amount);

        // 1. Validate amount input first (fail fast)
        if (amount == null || amount.trim().isEmpty()) {
            throw new FunctionException("Termination amount is required", null);
        }

        BigDecimal amountToTerminate;
        try {
            amountToTerminate = new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            throw new FunctionException("Invalid termination amount format", null);
        }

        if (amountToTerminate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FunctionException("Termination amount must be greater than zero", null);
        }

        // 2. Validate User
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        // 3. Validate PIN
        if (!passwordEncoder.matches(pin, user.getPin())) {
            log.warn("Investment termination failed: incorrect PIN | email={}", email);
            throw new FunctionException("Incorrect PIN", null);
        }

        // validate withdrawal account
        List<WithdrawalAccount> withdrawalAccounts = withdrawalAccountRespository.findByUser_Id(user.getId());
        if (withdrawalAccounts.isEmpty())
            throw new FunctionException("Add a withdrawal account to continue", null);

        // 4. Validate Investment
        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new ResourceNotFound("Investment not found"));

        if (investment.getInvestmentStatus() != Investment.InvestmentStatus.RUNNING) {
            log.warn("Cannot terminate non-running investment | email={} | investmentId={} | status={}", email, investmentId, investment.getInvestmentStatus());
            throw new FunctionException("This investment is not active and cannot be terminated", null);
        }

        // 5. Safe financial calculation (qty * price)
        BigDecimal qty;
        BigDecimal price;

        try {
            qty = new BigDecimal(investment.getRequestedQty());
            price = new BigDecimal(investment.getPrice());
        } catch (Exception e) {
            throw new FunctionException("Invalid investment financial data", null);
        }

        BigDecimal investedAmount = qty.multiply(price);

        // 6. Validate termination amount against invested amount
        if (amountToTerminate.compareTo(investedAmount) > 0) {
            throw new FunctionException("Termination amount cannot exceed invested amount", null);
        }

        boolean isPartialTermination = amountToTerminate.compareTo(investedAmount) < 0;

        if (isPartialTermination) {

            BigDecimal remainingAmount = investedAmount.subtract(amountToTerminate);

            // ⚠️ IMPORTANT: Do NOT overwrite price with remaining amount
            // Instead calculate new qty OR store remaining value properly

            Investment terminatedInvestment = Investment.builder()
                    .product(investment.getProduct())
                    .accountId(investment.getAccountId())
                    .orderType(investment.getOrderType())
                    .marketCode(investment.getMarketCode())
                    .secId(investment.getSecId())
                    .requestedQty(investment.getRequestedQty())
                    .requestedVal(amountToTerminate.toPlainString())
                    .createdAt(LocalDateTime.now())
                    .price(investment.getPrice())
                    .requestDate(investment.getRequestDate())
                    .currency(investment.getCurrency())
                    .investmentStatus(Investment.InvestmentStatus.TERMINATED)
                    .channel(investment.getChannel())
                    .autoApprove(investment.getAutoApprove())
                    .autoPayRedemption(investment.getAutoPayRedemption())
                    .user(user)
                    .notes("Partial termination request: NGN " + amountToTerminate)
                    .build();

            investmentRepository.save(terminatedInvestment);

            // Better: store remaining value in requestedVal (NOT price)
            investment.setRequestedVal(remainingAmount.toPlainString());
            investment.setNotes(
                    "Partial termination: NGN " + amountToTerminate +
                            " terminated, NGN " + remainingAmount + " remaining"
            );
            investmentRepository.save(investment);

            Transaction transaction = new Transaction();
            transaction.setType(TransactionType.INVESTMENT_TERMINATION);
            transaction.setUser(user);
            transaction.setInvestment(terminatedInvestment);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setAmount(amountToTerminate);
            transaction.setStatus(TransactionStatus.PENDING);
            transaction.setAction(TransactionAction.DEBIT);
            transaction.setTxRef("TERM-" + System.currentTimeMillis());
            transaction.setNarration("Partial termination request");
            transaction.setSource("Investment Partial Termination");
            transactionRepository.save(transaction);

            emailService.sendInvestmentTerminationRequestMail(
                    "wecare@myrtleng.com",
                    user,
                    withdrawalAccounts.get(0),
                    investment.getProduct(),
                    amountToTerminate.doubleValue()
            );

            log.info("Partial investment termination submitted | email={} | investmentId={} | terminatedAmount={} | remainingAmount={}", email, investmentId, amountToTerminate, remainingAmount);
            return new FunctionResponse(
                    true,
                    HttpStatus.OK,
                    "Partial termination of NGN " + amountToTerminate +
                            " submitted. Remaining investment: NGN " + remainingAmount,
                    null
            );
        }

        // Full termination
        investment.setInvestmentStatus(Investment.InvestmentStatus.TERMINATED);
        investment.setNotes("Full termination request: NGN " + amountToTerminate);
        investmentRepository.save(investment);

        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.INVESTMENT_TERMINATION);
        transaction.setUser(user);
        transaction.setInvestment(investment);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setAmount(amountToTerminate);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setAction(TransactionAction.DEBIT);
        transaction.setTxRef("TERM-" + System.currentTimeMillis());
        transaction.setNarration("Full termination request");
        transaction.setSource("Investment Termination");
        transactionRepository.save(transaction);

        emailService.sendInvestmentTerminationRequestMail(
                "wecare@myrtleng.com",
                user,
                withdrawalAccounts.get(0),
                investment.getProduct(),
                amountToTerminate.doubleValue()
        );

        log.info("Full investment termination submitted | email={} | investmentId={} | amount={}", email, investmentId, amountToTerminate);
        return new FunctionResponse(
                true,
                HttpStatus.OK,
                "Full termination request of NGN " + amountToTerminate +
                        " submitted and pending approval.",
                null
        );
    }

    public FunctionResponse withdrawalInvestment(String email, String pin, String productId, String amount) {
        log.info("Investment withdrawal requested | email={} | productId={} | amount={}", email, productId, amount);

        // Corporate accounts cannot self-service withdrawals - the Myrtle team handles them.
        if (corporateUserPolicy.isCorporateUser(email)) {
            log.info("Blocked investment withdrawal for corporate user | email={}", email);
            return FunctionResponse.builder()
                    .isSuccess(false)
                    .status(HttpStatus.FORBIDDEN)
                    .message(CorporateUserPolicy.MONEY_OUT_BLOCKED_MESSAGE)
                    .data(null)
                    .build();
        }

        // 1. Validate inputs (fail fast)
        BigDecimal amountToTerminate = parseAndValidateAmount(amount);
        validateNotBlank(pin, "PIN is required");
        validateNotBlank(productId, "Product ID is required");

        // 2. Resolve user and verify PIN
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));
        if (user.getPin() == null || !passwordEncoder.matches(pin, user.getPin())) {
            log.warn("Investment withdrawal failed: incorrect PIN | email={}", email);
            throw new FunctionException("Incorrect PIN", null);
        }

        // validate withdrawal account
        List<WithdrawalAccount> withdrawalAccounts = withdrawalAccountRespository.findByUser_Id(user.getId());
        if (withdrawalAccounts.isEmpty())
            throw new FunctionException("Add a withdrawal account to continue", null);

        // 3. Resolve product
        Product product = productRespository.findById(productId.trim())
                .orElseThrow(() -> new FunctionException("Product Not Found", null));

        // 4. Ensure user has investment account for balance check
        String accountId = user.getInvestmentAccountId();
        if (accountId == null || accountId.isBlank()) {
            throw new FunctionException("Investment account not set up for this user", null);
        }

        // 5. Fetch current balance from ledger
        LedgerStatementRequest ledgerRequest = new LedgerStatementRequest();
        ledgerRequest.setAccountId(accountId);
        ledgerRequest.setMarketCode(LEDGER_MARKET_CODE);
        ledgerRequest.setSecId(product.getCode());
        ledgerRequest.setStartDate(LEDGER_START_DATE);
        ledgerRequest.setEndDate(String.valueOf(LocalDate.now()));

        LedgerStatementResponse ledgerResponse;
        try {
            ledgerResponse = externalAPIs.getRateStats(ledgerRequest);
        } catch (Exception e) {
            throw new FunctionException("Unable to verify balance. Please try again later.", null);
        }

        double valueToWithdraw = amountToTerminate.doubleValue();
        BigDecimal availableBalance = BigDecimal.valueOf(Math.max(0, ledgerResponse.getClosingBalance()));

        if (amountToTerminate.compareTo(availableBalance) > 0) {
            log.info("user requested to withdraw {}, but available balance is: {}. Reverting to available balance.", amountToTerminate, availableBalance);
//            throw new FunctionException("Insufficient balance", null);
            valueToWithdraw = availableBalance.doubleValue();
        }

        // 6. Send termination request email
        emailService.sendInvestmentTerminationRequestMail(
                SUPPORT_EMAIL,
                user,
                withdrawalAccounts.get(0),
                product,
                valueToWithdraw
        );

        WithdrawalHistory history = new WithdrawalHistory(amount, product, user);
        withdrawalHistoryRepository.save(history);

        log.info("Investment withdrawal submitted | email={} | productId={} | amount={}", email, productId, amountToTerminate);
        String message = "Termination request submitted and pending approval.";

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message(message)
                .data(null)
                .build();
    }

    public FunctionResponse terminateAllInvestment(String email, String pin, String productId) {
        log.info("Bulk investment termination requested | email={} | productId={}", email, productId);

        // 1. Validate User
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        // 2. Validate PIN
        if (!passwordEncoder.matches(pin, user.getPin())) {
            log.warn("Bulk termination failed: incorrect PIN | email={}", email);
            throw new FunctionException("Incorrect PIN", null);
        }

        Optional<Product> product = productRespository.findById(productId);

        if (product.isEmpty()) {
            throw new FunctionException("Product Not Found", null);
        }

        // 3. Fetch ONLY running investments for this user & product
        List<Investment> investments = investmentRepository
                .findByUser_IdAndProduct_IdAndInvestmentStatus(
                        user.getId(),
                        productId,
                        Investment.InvestmentStatus.RUNNING
                );

        if (investments.isEmpty()) {
            throw new FunctionException("No active investments found for this product", null);
        }

        // 4. Calculate total investment value safely (qty * price)
        BigDecimal totalAmount = investments.stream()
                .map(inv -> {
                    try {
                        BigDecimal qty = new BigDecimal(
                                inv.getRequestedQty() == null ? "0" : inv.getRequestedQty()
                        );
                        BigDecimal price = new BigDecimal(
                                inv.getPrice() == null ? "0" : inv.getPrice()
                        );
                        return qty.multiply(price);
                    } catch (NumberFormatException e) {
                        return BigDecimal.ZERO; // fail-safe for dirty DB values
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FunctionException("Total investment value is zero, cannot terminate", null);
        }

        // 5. Mark all investments as TERMINATED (batch logic)
        LocalDateTime now = LocalDateTime.now();

        for (Investment investment : investments) {
            investment.setInvestmentStatus(Investment.InvestmentStatus.TERMINATED);
            investment.setNotes(
                    "Bulk termination request submitted on " + now +
                            " for productId: " + productId
            );
        }

        investmentRepository.saveAll(investments);

        // 6. Create a SINGLE aggregated termination transaction (recommended)
        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.INVESTMENT_TERMINATION);
        transaction.setUser(user);
        transaction.setCreatedAt(now);
        transaction.setAmount(totalAmount);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setAction(TransactionAction.DEBIT);
        transaction.setTxRef("TERM-ALL-" + System.currentTimeMillis());
        transaction.setNarration("Full termination of all running investments for product");
        transaction.setSource("Bulk Investment Termination Request");

        transactionRepository.save(transaction);


        // 7. Send Email (optional but good for audit/compliance)
        emailService.sendBulkInvestmentTerminationMail(
                "wecare@myrtleng.com",
                user,
                product.get().getLabel(),
                investments,
                totalAmount
        );

        log.info("Bulk investment termination submitted | email={} | productId={} | count={} | totalAmount={}", email, productId, investments.size(), totalAmount);
        // 8. Response
        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("All active investments for this product have been submitted for termination. Total value: NGN "
                        + totalAmount)
                .data(null)
                .build();
    }

    public FunctionResponse getRealRates(String email, RealRateRequest request) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        try {
            ClientPortfolioResponse response = externalAPIs.clientPortfolioResponse(user.getClientId(), Currency.valueOf(request.getCurrency().name()));

            return FunctionResponse.builder()
                    .isSuccess(true)
                    .status(HttpStatus.OK)
                    .message("Real Rates Fetched Successfully")
                    .data(response)
                    .build();

        } catch (Exception ex) {
            throw new FunctionException("Unable to fetch real rates at this time", Map.of(
                    "error", ex.getMessage()
            ));
        }
    }

    // Fetch single investment by ID
    public FunctionResponse fetchAllRunningInvestmentsById(String email, String id) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        Investment investment = investmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Investment not found with ID: " + id));

        InvestmentDTO dto = mapToDTO(investment);

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Investment fetched successfully")
                .data(dto)
                .build();
    }

    // Generate Ref ID CODE
    public FunctionResponse generateRefCode() {
        String refCode = UUID.randomUUID().toString();

        Map<String, String> response = new HashMap<>();

        response.put("refCode", refCode);
        return FunctionResponse.builder()
                .message("Reference Generated Successfully")
                .status(HttpStatus.OK)
                .isSuccess(true)
                .data(response)
                .build();
    }

    // Initiate Dollar Shield Transaction
    public FunctionResponse initiateDollarShieldTransaction(String email, DollarShieldRequest payload) {

        // 1. Validate User
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        // 1. Validate PIN
        if (!passwordEncoder.matches(payload.getPin(), user.getPin())) {
            throw new FunctionException("Incorrect PIN", null);
        }

        // 1. Validate Product (used only for email context)
        Product product = productRespository.findById(payload.getProductId())
                .orElseThrow(() -> new ResourceNotFound("Product not found"));

        // 9. Generate approval token
        String token = jwtUtils.generateToken(email);
        String documentUrl = payload.getUrl();

        // Dummy reference (email tracking only)
        String ref = payload.getRefCode();

        //  Send Ops Email (email-only intent)
        emailService.sendInvestmentBookingNotification(
                user,
                product,
                documentUrl,
                ref
        );

        //  Response payload
        Map<String, Object> response = new HashMap<>();
        response.put("ref", ref);
        response.put("email", email);
        response.put("product", product.getLabel());

        //  Return success response
        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Dollar Shield request sent for operations approval.")
                .data(response)
                .build();
    }

    @Transactional
    public FunctionResponse confirmInvestment(String investmentId, String token, boolean approve) {
        log.info("Investment confirmation requested | investmentId={} | approve={}", investmentId, approve);

        // 1. Validate token
        String email = jwtUtils.extractUsername(token);
        if (email == null || email.isBlank()) {
            log.warn("Investment confirmation failed: invalid token | investmentId={}", investmentId);
            throw new FunctionException("This approval link is invalid or has expired.", null);
        }

        // 2. Fetch investment
        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new ResourceNotFound("Investment not found"));

        // 0. Idempotency check
        boolean alreadyProcessed = investment.getTransactions().stream()
                .anyMatch(tx ->
                        tx.getType() == TransactionType.INVESTMENT_CREATION &&
                                tx.getStatus() == TransactionStatus.SUCCESS
                );

        if (alreadyProcessed) {
            log.warn("Investment already processed | investmentId={}", investmentId);
            throw new FunctionException("This investment has already been processed.", null);
        }

        User user = investment.getUser();

        BigDecimal investmentAmount =
                new BigDecimal(investment.getPrice())
                        .multiply(new BigDecimal(investment.getRequestedQty()));

        //  Decline flow
        if (!approve) {
            investment.setInvestmentStatus(Investment.InvestmentStatus.FAILED);
            investment.setUpdatedAt(LocalDateTime.now());
            investmentRepository.save(investment);

            return FunctionResponse.builder()
                    .isSuccess(true)
                    .status(HttpStatus.OK)
                    .message("Investment declined successfully.")
                    .build();
        }

        // ===============================
        //  Rebuild InvestmentOrderRequest
        // ===============================
        //  Rebuild InvestmentOrderRequest (Moved up for clarity)
        InvestmentOrderRequest orderRequest = new InvestmentOrderRequest();
        orderRequest.setAccountId(investment.getAccountId());
        orderRequest.setOrderType(
                InvestmentOrderRequest.OrderType.valueOf(investment.getOrderType().name())
        );
        orderRequest.setMarketCode(investment.getMarketCode());
        orderRequest.setSecId(investment.getSecId());
        orderRequest.setRequestedQty(investment.getRequestedQty());
        orderRequest.setRequestedVal(investment.getRequestedVal());
        orderRequest.setPrice(investment.getPrice());
        orderRequest.setRequestDate(
                investment.getRequestDate().toLocalDate().toString()
        );
        orderRequest.setCurrency(
                InvestmentOrderRequest.Currency.valueOf(investment.getCurrency().name())
        );
        orderRequest.setNotes(investment.getNotes());
        orderRequest.setAutoApprove(investment.getAutoApprove());
        orderRequest.setAutoPayRedemption(investment.getAutoPayRedemption());
        orderRequest.setChannel(
                InvestmentOrderRequest.Channel.valueOf(investment.getChannel().name())
        );
        orderRequest.setRefCode(investment.getRefCode());

        //  Find Existing Pending Transaction
        Transaction pendingTransaction = investment.getTransactions().stream()
                .filter(tx -> tx.getType() == TransactionType.INVESTMENT_CREATION &&
                        tx.getStatus() == TransactionStatus.PENDING)
                .findFirst()
                .orElse(null);

        //  Decline flow
        if (!approve) {
            investment.setInvestmentStatus(Investment.InvestmentStatus.FAILED);
            investment.setUpdatedAt(LocalDateTime.now());
            investmentRepository.save(investment);

            // Update PENDING transaction to FAILED if it exists
            if (pendingTransaction != null) {
                pendingTransaction.setStatus(TransactionStatus.FAILED);
                pendingTransaction.setUpdatedAt(LocalDateTime.now());
                pendingTransaction.setNarration("Investment declined by operations");
                transactionRepository.save(pendingTransaction);
            }

            return FunctionResponse.builder()
                    .isSuccess(true)
                    .status(HttpStatus.OK)
                    .message("Investment declined successfully.")
                    .build();
        }

        // ===============================
        // 7. Create Cash Transaction Request (Deposit to Investment Account)
        // ===============================
        String valueDate = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        TransactionRequest cashRequest = new TransactionRequest(
                user.getInvestmentAccountId(),
                user.getBranchId(),
                user.getInvestmentAccountId(),
                TransactionRequest.DestAccountType.DEPOSIT,
                TransactionRequest.Channel.CASH,
                TransactionRequest.TransactionType.DEPOSIT,
                investmentAmount.doubleValue(),
                TransactionRequest.CurrencyCode.NGN,
                Generators.generateReference(),
                investment.getNotes(),
                valueDate,
                true,
                "",
                Generators.generateRefCode(),
                true
        );

        // 7. Create & Post Cash Transaction
        TransactionResponse walletTx =
                externalAPIs.createCashTransaction(cashRequest);

        if (walletTx == null || walletTx.getId() == null) {
            throw new FunctionException("Unable to debit wallet at this time.", null);
        }

        PostTransactionResponse postTx =
                externalAPIs.postTransaction(walletTx.getId());

        if (postTx == null) {
            throw new FunctionException("Failed to post wallet transaction.", null);
        }

        // ===============================
        // 7. Create Investment with External API
        // ===============================
        FundTransactionResponse fundTx =
                externalAPIs.createTransaction(orderRequest, null);

        if (fundTx == null || fundTx.getId() == null) {
            throw new FunctionException("Unable to complete investment transaction.", null);
        }

        // ===============================
        // Update Transaction (Instead of creating new redundant one)
        // ===============================
        if (pendingTransaction != null) {
            pendingTransaction.setTxRef(fundTx.getId());
            pendingTransaction.setStatus(TransactionStatus.SUCCESS);
            pendingTransaction.setNarration("Investment approved by operations");
            pendingTransaction.setSource("Dollar Shield Approval");
            pendingTransaction.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(pendingTransaction);
        } else {
            // Fallback: Create new if for some reason it wasn't there
            Transaction approvalTx = new Transaction();
            approvalTx.setInvestment(investment);
            approvalTx.setUser(user);
            approvalTx.setType(TransactionType.INVESTMENT_CREATION);
            approvalTx.setAmount(investmentAmount);
            approvalTx.setTxRef(fundTx.getId());
            approvalTx.setStatus(TransactionStatus.SUCCESS);
            approvalTx.setAction(TransactionAction.CREDIT);
            approvalTx.setNarration("Investment approved by operations");
            approvalTx.setSource("Dollar Shield Approval");
            approvalTx.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(approvalTx);
        }

        //  Update Investment Status
        investment.setInvestmentStatus(Investment.InvestmentStatus.RUNNING);
        investment.setUpdatedAt(LocalDateTime.now());
        investmentRepository.save(investment);

        log.info("Investment approved and set to RUNNING | investmentId={} | fundTransactionId={}", investment.getId(), fundTx.getId());
        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Investment approved successfully.")
                .data(Map.of(
                        "investmentId", investment.getId(),
                        "transactionId", fundTx.getId()
                ))
                .build();
    }

    public FunctionResponse getProductTotalInvestmentAmountByProductId(String id) {
        Product product = productRespository.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Product not Found"));

        List<Investment> productInvestments = investmentRepository.findAll().stream()
                .filter(investment ->
                        investment.getProduct() != null
                                && product.getId().equals(investment.getProduct().getId())
                )
                .toList();

        BigDecimal totalAmount = productInvestments.stream()
                .map(investment -> {
                    if (investment.getRequestedVal() != null && !investment.getRequestedVal().isBlank()) {
                        try {
                            return new BigDecimal(investment.getRequestedVal());
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    if (investment.getRequestedQty() != null && !investment.getRequestedQty().isBlank()
                            && investment.getPrice() != null && !investment.getPrice().isBlank()) {
                        try {
                            return new BigDecimal(investment.getRequestedQty())
                                    .multiply(new BigDecimal(investment.getPrice()));
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> data = new HashMap<>();
        data.put("productId", product.getId());
        data.put("productName", product.getLabel());
        data.put("investmentCount", productInvestments.size());
        data.put("totalInvestmentAmount", totalAmount);

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Product total investment amount fetched successfully")
                .data(data)
                .build();
    }

    public FunctionResponse getInvestmentsPerProductById(String id, String email) {
        return getInvestmentsPerProductById(email, id, null, 0, 10);
    }

    public FunctionResponse getInvestmentsPerProductById(String email, String id, InvestmentFilterRequest filter,
                                                         int page, int size) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        Product product = productRespository.findById(id)
                .orElseThrow(() -> new ResourceNotFound("Product not Found"));

        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());

        Page<Investment> investmentPage = investmentRepository.findByProduct_IdAndUser_Id(
                product.getId(),
                user.getId(),
                pageable
        );

        List<InvestmentDTO> investmentDTOs = investmentPage.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("productId", product.getId());
        responseData.put("productName", product.getLabel());
        responseData.put("content", investmentDTOs);
        responseData.put("currentPage", investmentPage.getNumber());
        responseData.put("totalItems", investmentPage.getTotalElements());
        responseData.put("totalPages", investmentPage.getTotalPages());
        responseData.put("pageSize", investmentPage.getSize());
        responseData.put("filters", filter);

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Investments fetched successfully")
                .data(responseData)
                .build();
    }


    public FunctionResponse getAllWithdrawalHistory(String email, String productId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 20;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());
        User user = userRepository.findByEmailAddressIgnoreCase(email).get();
        Page<WithdrawalHistory> withdrawalPage = withdrawalHistoryRepository.findByUser_idAndProductId(user.getId(), productId, pageable);

        List<WithdrawalDTO> content = withdrawalPage.getContent().stream()
                .map(this::mapHistoryDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("content", content);
        responseData.put("currentPage", withdrawalPage.getNumber());
        responseData.put("totalItems", withdrawalPage.getTotalElements());
        responseData.put("totalPages", withdrawalPage.getTotalPages());
        responseData.put("pageSize", withdrawalPage.getSize());

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Withdrawal history fetched successfully")
                .data(responseData)
                .build();
    }

    private WithdrawalDTO mapHistoryDTO(WithdrawalHistory history) {
        return WithdrawalDTO.builder()
                .amount(history.getAmount())
                .product(history.getProduct().getLabel())
                .transactionDateTime(history.getCreatedAt())
                .build();
    }

    // Mapper
    private InvestmentDTO mapToDTO(Investment investment) {
        return InvestmentDTO.builder()
                .id(investment.getId())
                .productId(investment.getProduct() != null ? investment.getProduct().getId() : null)
                .accountId(investment.getAccountId())
                .orderType(investment.getOrderType())
                .marketCode(investment.getMarketCode())
                .secId(investment.getSecId())
                .requestedQty(investment.getRequestedQty())
                .requestedVal(investment.getRequestedVal())
                .price(investment.getPrice())
                .requestDate(investment.getRequestDate())
                .currency(investment.getCurrency())
                .notes(investment.getNotes())
                .autoApprove(investment.getAutoApprove())
                .autoPayRedemption(investment.getAutoPayRedemption())
                .channel(investment.getChannel())
                .refCode(investment.getRefCode())
                .investmentStatus(investment.getInvestmentStatus())
                .createdAt(investment.getCreatedAt())
                .userId(investment.getUser() != null ? investment.getUser().getId() : null)
                .build();
    }

}
