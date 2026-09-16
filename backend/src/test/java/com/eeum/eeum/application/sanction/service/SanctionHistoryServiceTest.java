package com.eeum.eeum.application.sanction.service;

import com.eeum.eeum.application.sanction.dto.response.SanctionHistoryResponseDto;
import com.eeum.eeum.domain.sanction.entity.SanctionHistory;
import com.eeum.eeum.domain.sanction.enums.SanctionAction;
import com.eeum.eeum.domain.sanction.enums.SanctionSource;
import com.eeum.eeum.domain.sanction.enums.SanctionTargetType;
import com.eeum.eeum.domain.sanction.repository.SanctionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SanctionHistoryServiceTest {

    @InjectMocks
    private SanctionHistoryService sanctionHistoryService;

    @Mock
    private SanctionHistoryRepository sanctionHistoryRepository;

    @Test
    void 관리자가_회원을_정지하면_직접_제재_이력을_저장한다() {
        // Given
        ArgumentCaptor<SanctionHistory> captor = ArgumentCaptor.forClass(SanctionHistory.class);

        // When
        sanctionHistoryService.recordDirectAccountAction(10L, SanctionAction.SUSPEND, 1L);

        // Then
        verify(sanctionHistoryRepository).save(captor.capture());
        SanctionHistory history = captor.getValue();
        assertThat(history.getTargetType()).isEqualTo(SanctionTargetType.ACCOUNT);
        assertThat(history.getTargetId()).isEqualTo(10L);
        assertThat(history.getAction()).isEqualTo(SanctionAction.SUSPEND);
        assertThat(history.getSource()).isEqualTo(SanctionSource.DIRECT_ADMIN);
        assertThat(history.getProcessedByAdminId()).isEqualTo(1L);
        assertThat(history.getSourceReportId()).isNull();
        assertThat(history.getAdminNote()).isNull();
    }

    @Test
    void 신고로_상점을_정지하면_신고와_관리자_사유를_저장한다() {
        // Given
        ArgumentCaptor<SanctionHistory> captor = ArgumentCaptor.forClass(SanctionHistory.class);

        // When
        sanctionHistoryService.recordReportAction(
                SanctionTargetType.STORE,
                20L,
                SanctionAction.SUSPEND,
                "운영 정책 위반",
                1L,
                100L
        );

        // Then
        verify(sanctionHistoryRepository).save(captor.capture());
        SanctionHistory history = captor.getValue();
        assertThat(history.getTargetType()).isEqualTo(SanctionTargetType.STORE);
        assertThat(history.getTargetId()).isEqualTo(20L);
        assertThat(history.getAction()).isEqualTo(SanctionAction.SUSPEND);
        assertThat(history.getSource()).isEqualTo(SanctionSource.REPORT);
        assertThat(history.getAdminNote()).isEqualTo("운영 정책 위반");
        assertThat(history.getProcessedByAdminId()).isEqualTo(1L);
        assertThat(history.getSourceReportId()).isEqualTo(100L);
    }

    @Test
    void 회원_제재_이력은_회원_대상만_페이징하여_반환한다() {
        // Given
        PageRequest pageable = PageRequest.of(0, 20);
        SanctionHistory history = SanctionHistory.record(
                SanctionTargetType.ACCOUNT,
                10L,
                SanctionAction.ACTIVATE,
                SanctionSource.DIRECT_ADMIN,
                null,
                1L,
                null
        );
        LocalDateTime createdAt = LocalDateTime.now();
        ReflectionTestUtils.setField(history, "sanctionHistoryId", 7L);
        ReflectionTestUtils.setField(history, "createdAt", createdAt);
        when(sanctionHistoryRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDescSanctionHistoryIdDesc(
                        SanctionTargetType.ACCOUNT,
                        10L,
                        pageable
                ))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        // When
        Page<SanctionHistoryResponseDto> result =
                sanctionHistoryService.getAccountHistories(10L, pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        SanctionHistoryResponseDto response = result.getContent().get(0);
        assertThat(response.getSanctionHistoryId()).isEqualTo(7L);
        assertThat(response.getTargetType()).isEqualTo(SanctionTargetType.ACCOUNT);
        assertThat(response.getTargetId()).isEqualTo(10L);
        assertThat(response.getAction()).isEqualTo(SanctionAction.ACTIVATE);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }
}
