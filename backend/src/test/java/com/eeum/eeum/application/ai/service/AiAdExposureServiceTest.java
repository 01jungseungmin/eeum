package com.eeum.eeum.application.ai.service;

import com.eeum.eeum.application.ai.dto.response.AiExposedStoreDto;
import com.eeum.eeum.domain.ai.entity.AiExposureStatus;
import com.eeum.eeum.domain.ai.repository.AiAdClickLogRepository;
import com.eeum.eeum.domain.ai.repository.AiAdExposureLogRepository;
import com.eeum.eeum.domain.ai.repository.AiExposureStatusRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAdExposureServiceTest {

    @InjectMocks
    private AiAdExposureService exposureService;

    @Mock private AiExposureStatusRepository aiExposureStatusRepository;
    @Mock private AiAdExposureLogRepository aiAdExposureLogRepository;
    @Mock private AiAdClickLogRepository aiAdClickLogRepository;

    private AiExposureStatus activeExposure(Long id, String address) {
        Store store = mock(Store.class);
        lenient().when(store.getStoreId()).thenReturn(id);
        lenient().when(store.getName()).thenReturn("상점" + id);
        lenient().when(store.getAddress()).thenReturn(address);
        AiExposureStatus status = AiExposureStatus.init(store);
        status.start(LocalDateTime.now(), 10);
        ReflectionTestUtils.setField(status, "aiExposureStatusId", id);
        return status;
    }

    @Test
    void 노출_시작한_가게만_사용자_노출_목록에_포함되고_노출_로그가_저장된다() {
        // given — mock 생성은 when() 밖에서 수행 (중첩 스터빙 방지)
        AiExposureStatus exposure = activeExposure(1L, "서울 마포구 서교동");
        when(aiExposureStatusRepository.findByActiveTrue()).thenReturn(List.of(exposure));

        // when
        List<AiExposedStoreDto> stores = exposureService.getExposedStores(null, null);

        // then
        assertThat(stores).hasSize(1);
        assertThat(stores.get(0).getStoreId()).isEqualTo(1L);
        verify(aiAdExposureLogRepository).save(any());
    }

    @Test
    void 지역_키워드가_일치하지_않는_가게는_노출에서_제외된다() {
        // given
        AiExposureStatus exposure = activeExposure(1L, "서울 강남구 역삼동");
        when(aiExposureStatusRepository.findByActiveTrue()).thenReturn(List.of(exposure));

        // when
        List<AiExposedStoreDto> stores = exposureService.getExposedStores(null, "마포구");

        // then
        assertThat(stores).isEmpty();
        verify(aiAdExposureLogRepository, never()).save(any());
    }

    @Test
    void 노출_중인_가게가_없어도_빈_리스트로_정상_응답한다() {
        // given
        when(aiExposureStatusRepository.findByActiveTrue()).thenReturn(List.of());

        // when & then
        assertThat(exposureService.getExposedStores(10L, null)).isEmpty();
    }

    @Test
    void 클릭_시_클릭_로그가_저장된다() {
        // given
        AiExposureStatus exposure = activeExposure(1L, "서울 마포구");
        when(aiExposureStatusRepository.findById(1L)).thenReturn(Optional.of(exposure));

        // when
        exposureService.recordClick(1L, 10L, "req-1");

        // then
        verify(aiAdClickLogRepository).save(any());
    }

    @Test
    void 존재하지_않는_노출_ID_클릭은_예외가_발생한다() {
        // given
        when(aiExposureStatusRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> exposureService.recordClick(99L, null, null))
                .isInstanceOf(BusinessException.class);
    }
}
