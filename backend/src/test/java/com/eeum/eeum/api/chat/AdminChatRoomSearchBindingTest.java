package com.eeum.eeum.api.chat;

import com.eeum.eeum.application.chat.dto.request.ChatRoomAdminSearchDto;
import com.eeum.eeum.application.chat.service.AdminChatService;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 조건 DTO가 실제 쿼리 파라미터로 채워지는지 고정한다.
 *
 * 서비스 단위 테스트는 조건 객체를 직접 만들어 넘기므로 바인딩 실패를 잡지 못한다.
 * 바인딩이 깨지면 예외 없이 모든 필드가 null이 되어 필터가 통째로 무시된다.
 */
@WebMvcTest(AdminChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AdminChatRoomSearchBindingTest {

    private final MockMvc mockMvc;
    @MockitoBean private AdminChatService adminChatService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void 쿼리_파라미터가_검색_조건_DTO에_바인딩된다() throws Exception {
        // Given
        when(adminChatService.getAllRooms(any(), any())).thenReturn(Page.empty());

        // When
        mockMvc.perform(get("/admin/chat/rooms")
                        .param("type", "GROUP_STREET")
                        .param("isActive", "false")
                        .param("from", "2026-09-01T00:00:00")
                        .param("to", "2026-09-30T23:59:59"))
                .andExpect(status().isOk());

        // Then
        ArgumentCaptor<ChatRoomAdminSearchDto> captor =
                ArgumentCaptor.forClass(ChatRoomAdminSearchDto.class);
        verify(adminChatService).getAllRooms(captor.capture(), any(Pageable.class));

        ChatRoomAdminSearchDto condition = captor.getValue();
        assertThat(condition.getType()).isEqualTo(ChatRoomType.GROUP_STREET);
        assertThat(condition.getIsActive()).isFalse();
        assertThat(condition.getFrom()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0, 0));
        assertThat(condition.getTo()).isEqualTo(LocalDateTime.of(2026, 9, 30, 23, 59, 59));
    }

    @Test
    void 파라미터를_주지_않으면_조건은_모두_null이다() throws Exception {
        // Given
        when(adminChatService.getAllRooms(any(), any())).thenReturn(Page.empty());

        // When
        mockMvc.perform(get("/admin/chat/rooms")).andExpect(status().isOk());

        // Then
        ArgumentCaptor<ChatRoomAdminSearchDto> captor =
                ArgumentCaptor.forClass(ChatRoomAdminSearchDto.class);
        verify(adminChatService).getAllRooms(captor.capture(), any(Pageable.class));

        ChatRoomAdminSearchDto condition = captor.getValue();
        assertThat(condition.getType()).isNull();
        assertThat(condition.getIsActive()).isNull();
        assertThat(condition.getFrom()).isNull();
        assertThat(condition.getTo()).isNull();
    }
}
