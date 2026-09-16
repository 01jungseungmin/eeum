package com.eeum.eeum.application.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class RefundRequestDto {

    @NotBlank(message = "환불 사유를 입력해주세요.")
    @Size(max = 500)
    private String reason;
}