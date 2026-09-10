package com.eeum.eeum.application.settlement.dto.request;
import jakarta.validation.constraints.NotBlank;
public record ManualPayoutCompleteRequestDto(@NotBlank String claimToken, @NotBlank String payoutReference) {}
