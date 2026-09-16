package com.eeum.eeum.application.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class OrderRejectRequestDto {

    @NotBlank(message = "거절 사유를 입력해주세요.")
    @Size(max = 500)
    private String reason;
}
