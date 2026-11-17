package com.myrtle.middleware.services.products;

import com.myrtle.middleware.dtos.request.ViewSingleProductResponse;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.ClientPortfolioResponse;
import com.myrtle.middleware.dtos.response.GoalsResponse;
import com.myrtle.middleware.enums.Currency;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.GeneralAccount;
import com.myrtle.middleware.model.Investment;
import com.myrtle.middleware.model.Product;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.*;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ExternalAPIs externalAPIs;
    private final ProductRespository productRespository;
    private final GeneralAccountRepository generalAccountRepository;
    private final InvestmentRepository investmentRepository;
    private final RunRawQuery runRawQuery;
    private final ResponseUtil responseUtil;
    private final UserRepository userRepository;

    public ResponseEntity<ApiResponse> addProduct(Product product) {
        log.info("Adding product | code={} | label={}", product.getCode(), product.getLabel());
        Optional<Product> existing = productRespository.findProductByCode(product.getCode());

        if (existing.isPresent()) {
            log.warn("Product already exists | code={}", product.getCode());
            return responseUtil.conflict("Product already exists", null);
        }
        Product pd = productRespository.save(product);
        log.info("Product added | productId={} | code={}", pd.getId(), pd.getCode());
        return responseUtil.success("Investment products added successfully.", pd);
    }

    public ResponseEntity<ApiResponse> addLimitToProduct(String id, String initialLimit, String minimumLimit) {
        log.info("Updating product limits | productId={} | initialLimit={} | minimumLimit={}", id, initialLimit, minimumLimit);
        Optional<Product> existing = productRespository.findById(id);
        if (existing.isEmpty()) {
            return responseUtil.notFound("Product not found", null);
        }
        if ((initialLimit == null || initialLimit.isBlank()) &&
                (minimumLimit == null || minimumLimit.isBlank())) {
            return responseUtil.badRequest("At least one limit value (initial or minimum) is required", null);
        }

        Product product = existing.get();
        if (initialLimit != null && !initialLimit.isBlank()) {
            product.setInitialLimit(initialLimit);
        }
        if (minimumLimit != null && !minimumLimit.isBlank()) {
            product.setMinimumLimit(minimumLimit);
        }
        product.setIsInitialLimitPass(false);
        productRespository.save(product);
        return responseUtil.success("Product limits updated successfully", product);
    }

    public ViewSingleProductResponse viewSingleProducts(String productId, String userName) {
        log.debug("Viewing single product | productId={} | user={}", productId, userName);
        Optional<User> optionalUser = userRepository.findByEmailAddressIgnoreCase(userName);

        if (optionalUser.isEmpty()) {
            log.warn("Unauthorized product view: user not found | user={}", userName);
            new ApiResponse(false, "Unauthorized", null);
        }
        User dbUser = optionalUser.get();

        Optional<Product> existing = productRespository.findById(productId);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Investment> userInvestments = investmentRepository.findByProduct_IdAndUser_Id(productId, dbUser.getId(), pageable);
        boolean userInitialInvestment = !(userInvestments.getTotalElements() > 0);

        if (existing.isEmpty()) {
            return ViewSingleProductResponse.builder()
                    .isSuccess(false)
                    .status(HttpStatus.CONFLICT)
                    .data(null)
                    .message("Product Not Found")
                    .totalInvestmentAmount("0")
                    .build();
        }

        Optional<GeneralAccount> generalAccount = generalAccountRepository.findByProductCodeIgnoreCase(existing.get().getCode());

        if (generalAccount.isEmpty()) {
            return ViewSingleProductResponse.builder()
                    .isSuccess(false)
                    .status(HttpStatus.CONFLICT)
                    .data(null)
                    .message("Settlement Account Not Found")
                    .totalInvestmentAmount("0")
                    .build();
        }

        BigDecimal totalInvestmentAmount = BigDecimal.ZERO;
        Optional<User> userOpt = userRepository.findById(dbUser.getId());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getClientId() != null && !user.getClientId().isBlank()) {
                ClientPortfolioResponse response = externalAPIs.clientPortfolioResponse(user.getClientId(), Currency.NGN);
                if (response != null && response.getInvestmentAccounts() != null) {
                    String productCode = existing.get().getCode();
                    totalInvestmentAmount = response.getInvestmentAccounts().stream()
                            .filter(Objects::nonNull)
                            .map(ClientPortfolioResponse.InvestmentAccount::getPositions)
                            .filter(Objects::nonNull)
                            .flatMap(List::stream)
                            .filter(Objects::nonNull)
                            .filter(p -> productCode != null && productCode.equals(p.getSecId()))
                            .map(p -> {
                                BigDecimal b = p.getReportValue();
                                if (b == null) b = p.getPositionValue();
                                if (b == null && p.getPrice() != null && p.getQuantity() != null) {
                                    b = p.getPrice().multiply(p.getQuantity());
                                }
                                return b != null ? b : BigDecimal.ZERO;
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }
            }
        }

        existing.get().setBankAccountName(generalAccount.get().getAccountName());
        existing.get().setBankAccountNumber(generalAccount.get().getAccountNumber());
        existing.get().setCurrency(String.valueOf(generalAccount.get().getCurrency()));
        existing.get().setBankName(generalAccount.get().getBankName());
        existing.get().setIsInitialLimitPass(checkIfInitialLimitPassed(existing.get(), totalInvestmentAmount));

        return ViewSingleProductResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .isUserInitialInvestment(userInitialInvestment)
                .totalInvestmentAmount(totalInvestmentAmount.toPlainString())
                .data(existing)
                .message("Product Fetched Successfully")
                .build();
    }

    public ResponseEntity<ApiResponse> deleteProduct(String id) {
        log.info("Deleting product | productId={}", id);
        Optional<Product> existing = productRespository.findById(id);

        if (existing.isEmpty()) {
            log.warn("Product delete failed: not found | productId={}", id);
            return responseUtil.conflict("Product Not Found", null);
        }
        productRespository.delete(existing.get());
        log.info("Product deleted | productId={} | code={}", id, existing.get().getCode());
        return responseUtil.success("Product Deleted Successfully", existing.get());
    }

    public ResponseEntity<ApiResponse> getInvestmentProducts() {
        log.debug("Fetching investment products from external API");
        List<GoalsResponse> goalsResponses = externalAPIs.getGoals();
        if (goalsResponses == null || goalsResponses.isEmpty()) {
            log.error("Investment products fetch returned empty or null from external API");
            return responseUtil.conflict("Failed to fetch investment products.", null);
        }
        log.debug("Investment products fetched | count={}", goalsResponses.size());
        return responseUtil.success("Investment products fetched successfully.", goalsResponses);
    }

    public ResponseEntity<ApiResponse> getLocalProduct(String email) {
        log.debug("Fetching local products | email={}", email);
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        List<Product> products = productRespository.findAll();

        Map<String, BigDecimal> balancesBySecId = new HashMap<>();

        if (user.getClientId() != null && !user.getClientId().isBlank()) {

            ClientPortfolioResponse response = externalAPIs.clientPortfolioResponse(user.getClientId(), Currency.NGN);

            if (response != null && response.getInvestmentAccounts() != null) {
                response.getInvestmentAccounts().stream()
                        .filter(Objects::nonNull)
                        .map(ClientPortfolioResponse.InvestmentAccount::getPositions)
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .filter(Objects::nonNull)
                        .forEach(position -> {

                            String secId = position.getSecId();
                            if (secId == null || secId.isBlank()) return;

                            BigDecimal balance = position.getReportValue();

                            if (balance == null) balance = position.getPositionValue();

                            if (balance == null &&
                                    position.getPrice() != null &&
                                    position.getQuantity() != null) {
                                balance = position.getPrice().multiply(position.getQuantity());
                            }

                            balancesBySecId.merge(
                                    secId,
                                    balance == null ? BigDecimal.ZERO : balance,
                                    BigDecimal::add
                            );
                        });
            }
        }

        List<Map<String, Object>> enrichedProducts = products.stream()
                .map(product -> {

                    BigDecimal productBalance = balancesBySecId.getOrDefault(product.getCode(), BigDecimal.ZERO);
                    boolean hasMetInitialLimit = checkIfInitialLimitPassed(product, productBalance);
                    product.setIsInitialLimitPass(hasMetInitialLimit);

                    Optional<GeneralAccount> accountOpt =
                            generalAccountRepository.findAll().stream()
                                    .filter(a -> a.getProductName().equalsIgnoreCase(product.getLabel()))
                                    .findFirst();

                    if (accountOpt.isPresent()) {
                        GeneralAccount account = accountOpt.get();
                        product.setBankAccountName(account.getAccountName());
                        product.setBankAccountNumber(account.getAccountNumber());
                        product.setBankName(account.getBankName());
                        product.setCurrency(account.getCurrency().name());
                    } else {
                        product.setBankAccountName(null);
                        product.setBankAccountNumber(null);
                        product.setCurrency(null);
                    }

                    BigDecimal investmentBalance =
                            balancesBySecId.getOrDefault(product.getCode(), BigDecimal.ZERO);

                    Map<String, Object> productMap = new LinkedHashMap<>();

                    productMap.put("id", product.getId());
                    productMap.put("code", product.getCode());
                    productMap.put("label", product.getLabel());
                    productMap.put("description", product.getDescription());
                    productMap.put("benefits", product.getBenefits());
                    productMap.put("pdId", product.getPdId());
                    productMap.put("profitPercentage", product.getProfitPercentage());
                    productMap.put("maturityDates", product.getMaturityDates());
                    productMap.put("productType", product.getProductType());

                    productMap.put("bankAccountName", product.getBankAccountName());
                    productMap.put("bankAccountNumber", product.getBankAccountNumber());
                    productMap.put("bankName", product.getBankName());
                    productMap.put("currency", product.getCurrency());

                    productMap.put("initialLimit", product.getInitialLimit());
                    productMap.put("minimumLimit", product.getMinimumLimit());
                    productMap.put("flutterwaveOnly", product.getFlutterwaveOnly());

                    productMap.put("isInitialLimitPass", product.getIsInitialLimitPass());

                    productMap.put("totalInvestmentAmount", investmentBalance.toPlainString());

                    return productMap;
                })
                .toList();

        return responseUtil.success(
                "Investment products fetched successfully.",
                enrichedProducts
        );
    }

    /**
     * Checks if the client's asset balance for this product (from client portfolio) meets or exceeds the product's initial limit.
     *
     * @param product        The product to check
     * @param productBalance The total balance for this product from {@code clientPortfolioResponse} (by secId/product code)
     * @return true if productBalance >= initial limit, false otherwise
     */
    private boolean checkIfInitialLimitPassed(Product product, BigDecimal productBalance) {
        if (product.getInitialLimit() == null || product.getInitialLimit().isBlank()) {
            return false;
        }
        if (productBalance == null) {
            return false;
        }
        try {
            BigDecimal initialLimit = new BigDecimal(product.getInitialLimit());
            return productBalance.compareTo(initialLimit) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public ResponseEntity<ApiResponse> deleteAllSettlements() {
        log.warn("Deleting ALL settlement accounts");
        generalAccountRepository.deleteAll();
        log.info("All settlement accounts deleted");
        return responseUtil.success("Deleted Successfully", null);
    }
}
