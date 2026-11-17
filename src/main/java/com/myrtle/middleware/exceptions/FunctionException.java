package com.myrtle.middleware.exceptions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FunctionException extends RuntimeException {
    private String message;
    private Object data;
}
