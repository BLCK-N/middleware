package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PagedLocationResponse {

    @JsonProperty("page")
    private int page;

    @JsonProperty("size")
    private int size;

    @JsonProperty("totalPages")
    private int totalPages;

    @JsonProperty("totalCount")
    private int totalCount;

    @JsonProperty("sort")
    private String sort;

    @JsonProperty("order")
    private String order;

    @JsonProperty("content")
    private List<LocationResponse> content;

    @Data
    public static class LocationResponse {

        @JsonProperty("id")
        private String id;

        @JsonProperty("createdBy")
        private String createdBy;

        @JsonProperty("createdAt")
        private LocalDateTime createdAt;

        @JsonProperty("updatedBy")
        private String updatedBy;

        @JsonProperty("updatedAt")
        private LocalDateTime updatedAt;

        @JsonProperty("deleted")
        private boolean deleted;

        @JsonProperty("status")
        private String status;

        @JsonProperty("type")
        private String type;

        @JsonProperty("code")
        private String code;

        @JsonProperty("country")
        private String country;

        @JsonProperty("label")
        private String label;

        @JsonProperty("notes")
        private String notes;
    }
}
