package com.smartservice.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRequest(
        @NotBlank @Size(max = 500) String message,
        @Size(max = 100) String requestId,
        @Size(max = 200) String confirmationToken) {
}
