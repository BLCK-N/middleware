package com.myrtle.middleware.services.investment;

import com.myrtle.middleware.dtos.request.FunctionResponse;
import com.myrtle.middleware.model.Investment;
import com.myrtle.middleware.repository.InvestmentRepository;
import com.myrtle.middleware.repository.ProductRespository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInvestmentsService {

    private final InvestmentRepository investmentRepository;
    private final ProductRespository productRespository;

    public FunctionResponse fetchAllInvestments() {
        log.debug("Admin: fetching all investments");
        List<Investment> investments = investmentRepository.findAll();
        log.info("Admin: investments fetched | count={}", investments.size());
        return FunctionResponse.builder()
                .isSuccess(true)
                .data(investments)
                .status(HttpStatus.OK)
                .message("Investments fetched successfully")
                .build();
    }
}
