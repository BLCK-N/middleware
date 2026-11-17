package com.myrtle.middleware.custom;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GenericRequest<T> {
    private String url;
    private Map<String, String> headers;
    private T body;
    private HttpMethodType method;
}
