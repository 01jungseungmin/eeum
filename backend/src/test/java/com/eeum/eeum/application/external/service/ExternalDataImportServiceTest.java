package com.eeum.eeum.application.external.service;

import com.eeum.eeum.domain.external.entity.ExternalDataImportHistory;
import com.eeum.eeum.domain.external.enums.ExternalImportStatus;
import com.eeum.eeum.domain.external.repository.ExternalBuildingEnergyStatRepository;
import com.eeum.eeum.domain.external.repository.ExternalDataImportHistoryRepository;
import com.eeum.eeum.domain.external.repository.ExternalEnergyUsageStatRepository;
import com.eeum.eeum.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalDataImportServiceTest {

    @InjectMocks
    private ExternalDataImportService importService;

    @Mock private ExternalEnergyUsageStatRepository energyUsageStatRepository;
    @Mock private ExternalBuildingEnergyStatRepository buildingEnergyStatRepository;
    @Mock private ExternalDataImportHistoryRepository importHistoryRepository;

    private MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 용도별_전력사용량_CSV_Import에_성공하면_통계와_이력이_저장된다() {
        // given
        MockMultipartFile file = csv("electric.csv", """
                기준년월,시도,시군구,용도,사용량,판매요금,고객호수
                202605,서울,마포구,일반용,12345.6,1500000,120
                202605,서울,마포구,산업용,54321.0,4000000,30
                """);
        when(importHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ExternalDataImportHistory history = importService.importElectricUsageType(file, null);

        // then
        assertThat(history.getStatus()).isEqualTo(ExternalImportStatus.SUCCESS);
        assertThat(history.getSuccessRows()).isEqualTo(2);
        assertThat(history.getFailedRows()).isZero();
        verify(energyUsageStatRepository).deleteBySourceIdAndSourcePeriod("15101311", "2026-05");
        verify(energyUsageStatRepository).saveAll(anyList());
    }

    @Test
    void 필수_컬럼이_없으면_실패_이력을_남기고_예외가_발생한다() {
        // given — 기준년월/사용량 컬럼 없음
        MockMultipartFile file = csv("wrong.csv", """
                이름,주소
                가게,서울
                """);
        when(importHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when & then
        assertThatThrownBy(() -> importService.importElectricUsageType(file, null))
                .isInstanceOf(BusinessException.class);
        verify(importHistoryRepository).save(any()); // FAILED 이력 저장
        verify(energyUsageStatRepository, never()).saveAll(anyList());
    }

    @Test
    void CSV가_아닌_파일은_거부된다() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.xlsx", "application/vnd.ms-excel", new byte[]{1, 2, 3});

        // when & then
        assertThatThrownBy(() -> importService.importElectricUsageType(file, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 빈_파일은_거부된다() {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        // when & then
        assertThatThrownBy(() -> importService.importElectricUsageType(file, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 건물에너지는_시군구_필터와_법정동_단위_집계로_적재된다() {
        // given — 마포구 2행은 같은 법정동이라 1행으로 집계, 강남구 1행은 필터로 제외
        MockMultipartFile file = csv("building.csv", """
                기준년월,시도,시군구,법정동,사용량
                202605,서울,마포구,서교동,100.5
                202605,서울,마포구,서교동,200.5
                202605,서울,강남구,역삼동,999.0
                """);
        when(importHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ExternalDataImportHistory history = importService.importBuildingElectricEnergy(file, null, "마포구");

        // then — 집계 후 1행만 저장
        assertThat(history.getSuccessRows()).isEqualTo(1);
        verify(buildingEnergyStatRepository).deleteBySourceIdAndSourcePeriod(eq("15054214"), eq("2026-05"));
        verify(buildingEnergyStatRepository).saveAll(anyList());
    }

    @Test
    void 같은_기준월_재적재_시_기존_데이터를_교체해_멱등하게_처리된다() {
        // given
        MockMultipartFile file = csv("electric.csv", """
                기준년월,시도,시군구,용도,사용량
                202605,서울,마포구,일반용,100.0
                """);
        when(importHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when — 두 번 Import
        importService.importElectricUsageType(file, null);
        importService.importElectricUsageType(file, null);

        // then — 각 Import마다 삭제 후 삽입 (중복 누적 없음)
        verify(energyUsageStatRepository, org.mockito.Mockito.times(2))
                .deleteBySourceIdAndSourcePeriod("15101311", "2026-05");
        verify(energyUsageStatRepository, org.mockito.Mockito.times(2)).saveAll(anyList());
    }
}
