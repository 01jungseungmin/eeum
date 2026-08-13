package com.eeum.eeum.application.report.dto.response;

import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MyReportResponseDtoTest {

    @Test
    void 사용자용_신고_DTO에는_관리자_내부정보가_없다() throws Exception {
        Set<String> properties = Arrays.stream(
                        Introspector.getBeanInfo(MyReportResponseDto.class).getPropertyDescriptors())
                .map(descriptor -> descriptor.getName())
                .collect(Collectors.toSet());

        assertThat(properties)
                .contains("reportId", "targetType", "targetId", "reason", "content",
                        "reporterId", "reporterName", "status", "action", "createdAt",
                        "updatedAt", "reportedAt", "processedAt")
                .doesNotContain("adminNote", "processedByAdminId", "actionTargetAccountId",
                        "reporterEmail", "reporterNickname");
    }
}
