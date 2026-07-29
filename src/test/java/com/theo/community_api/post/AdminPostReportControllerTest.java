package com.theo.community_api.post;

import com.theo.community_api.auth.jwt.JwtTokenProvider;
import com.theo.community_api.auth.security.CustomUserDetailsService;
import com.theo.community_api.auth.security.JwtAccessDeniedHandler;
import com.theo.community_api.auth.security.JwtAuthenticationEntryPoint;
import com.theo.community_api.auth.security.JwtAuthenticationFilter;
import com.theo.community_api.common.config.SecurityConfig;
import com.theo.community_api.post.controller.AdminPostReportController;
import com.theo.community_api.post.domain.PostReportStatus;
import com.theo.community_api.post.service.PostReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPostReportController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class AdminPostReportControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostReportService postReportService;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("미인증 사용자의 관리자 신고 목록 조회 요청은 401을 반환한다")
    void readReportList_unauthenticated() throws Exception {
        // given : 미인증 사용자 (@WithMockUser 사용 X로 표현)
        // when : get /admin/post-reports?status=PENDING
        mockMvc.perform(get("/admin/post-reports")
                        .param("status", "PENDING"))
                // then : http status code : 401 -> 미인증 사용자 error code 발생
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("access_token_required"));

        verifyNoInteractions(postReportService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("인증됐지만 관리자 권한이 없어 인가되지 않은 사용자의 요청은 403을 반환한다")
    void readReportList_forbidden_when_user_is_not_admin() throws Exception {
        // given : 인증된 사용자 (@WithMockUser roles = "USER")
        // when : get /admin/post-reports
        mockMvc.perform(get("/admin/post-reports")
                        .param("status", "PENDING"))
                // then : http status code : 403 -> 인가되지 않은 사용자
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("access_denied"));

        verifyNoInteractions(postReportService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자 권한 사용자의 신고 목록 조회 요청은 200을 반환한다")
    void readReportList_success() throws Exception {
        // given : 인가된 관리자 (@WithMockUser roles = "ADMIN")
        given(postReportService.readReportList(PostReportStatus.PENDING))
                .willReturn(List.of());

        // when : get /admin/post-reports
        mockMvc.perform(get("/admin/post-reports")
                        .param("status", "PENDING"))
                // then : http status code 200 -> ok
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("post_report_list_read_success"));

        verify(postReportService)
                .readReportList(PostReportStatus.PENDING);
    }
}
