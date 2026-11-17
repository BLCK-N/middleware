package com.myrtle.middleware.dtos;

import com.myrtle.middleware.enums.TransactionAction;
import com.myrtle.middleware.enums.TransactionStatus;
import com.myrtle.middleware.enums.TransactionType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class TransactionFilterRequest {
    private TransactionType type;
    private TransactionStatus status;
    private TransactionAction action;
    private String source;
    private String search;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAfter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdBefore;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedAfter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedBefore;
}
