package com.example.gdprkv.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PutRecordRequest(
        @JsonProperty("purpose") @NotBlank String purpose,
        @JsonProperty("value") JsonNode value
) {}
