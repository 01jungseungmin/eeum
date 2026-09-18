package com.eeum.eeum.application.account.service;

import com.eeum.eeum.application.account.dto.request.LocationDto;
import com.eeum.eeum.application.account.dto.response.AccountRegionResponseDto;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.infrastructure.geo.KakaoRegionCodeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountRegionVerificationServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long ACCOUNT_REGION_ID = 10L;
    private static final String YEONNAM_CODE = "1144012400";
    private static final double LATITUDE = 37.5627;
    private static final double LONGITUDE = 126.922;

    @Mock
    private AccountRegionService accountRegionService;

    @Mock
    private KakaoRegionCodeClient kakaoRegionCodeClient;

    @InjectMocks
    private AccountRegionVerificationService verificationService;

    @Test
    void 현재_좌표의_법정동이_등록한_동과_같으면_인증한다() {
        // given
        AccountRegionResponseDto expected = AccountRegionResponseDto.builder().build();
        given(accountRegionService.getRegionCode(ACCOUNT_ID, ACCOUNT_REGION_ID)).willReturn(YEONNAM_CODE);
        given(kakaoRegionCodeClient.findLegalDongCode(LATITUDE, LONGITUDE)).willReturn(Optional.of(YEONNAM_CODE));
        given(accountRegionService.completeVerification(ACCOUNT_ID, ACCOUNT_REGION_ID)).willReturn(expected);

        // when
        AccountRegionResponseDto result = verificationService.verifyRegion(ACCOUNT_ID, ACCOUNT_REGION_ID, location());

        // then
        assertThat(result).isSameAs(expected);
    }

    @Test
    void 다른_동에_있으면_위치_불일치로_거절하고_인증하지_않는다() {
        // given
        given(accountRegionService.getRegionCode(ACCOUNT_ID, ACCOUNT_REGION_ID)).willReturn(YEONNAM_CODE);
        given(kakaoRegionCodeClient.findLegalDongCode(LATITUDE, LONGITUDE)).willReturn(Optional.of("1144012100"));

        // when & then
        assertErrorCode(ErrorCode.REGION_GPS_MISMATCH);
        verify(accountRegionService, never()).completeVerification(anyLong(), anyLong());
    }

    @Test
    void 법정동이_없는_좌표면_위치_불일치로_거절한다() {
        // given
        given(accountRegionService.getRegionCode(ACCOUNT_ID, ACCOUNT_REGION_ID)).willReturn(YEONNAM_CODE);
        given(kakaoRegionCodeClient.findLegalDongCode(LATITUDE, LONGITUDE)).willReturn(Optional.empty());

        // when & then
        assertErrorCode(ErrorCode.REGION_GPS_MISMATCH);
        verify(accountRegionService, never()).completeVerification(anyLong(), anyLong());
    }

    @Test
    void 카카오_장애는_위치_불일치가_아닌_조회_불가로_전달하고_인증하지_않는다() {
        // given
        given(accountRegionService.getRegionCode(ACCOUNT_ID, ACCOUNT_REGION_ID)).willReturn(YEONNAM_CODE);
        given(kakaoRegionCodeClient.findLegalDongCode(LATITUDE, LONGITUDE))
                .willThrow(new BusinessException(ErrorCode.REGION_GEOCODE_UNAVAILABLE));

        // when & then
        assertErrorCode(ErrorCode.REGION_GEOCODE_UNAVAILABLE);
        verify(accountRegionService, never()).completeVerification(anyLong(), anyLong());
    }

    @Test
    void 본인_지역이_아니면_카카오를_호출하지_않는다() {
        // given
        given(accountRegionService.getRegionCode(ACCOUNT_ID, ACCOUNT_REGION_ID))
                .willThrow(new BusinessException(ErrorCode.REGION_NOT_FOUND));

        // when & then
        assertErrorCode(ErrorCode.REGION_NOT_FOUND);
        verifyNoInteractions(kakaoRegionCodeClient);
        verify(accountRegionService, never()).completeVerification(anyLong(), anyLong());
    }

    private void assertErrorCode(ErrorCode errorCode) {
        assertThatThrownBy(() -> verificationService.verifyRegion(ACCOUNT_ID, ACCOUNT_REGION_ID, location()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private LocationDto location() {
        LocationDto location = new LocationDto();
        ReflectionTestUtils.setField(location, "latitude", LATITUDE);
        ReflectionTestUtils.setField(location, "longitude", LONGITUDE);
        return location;
    }
}
