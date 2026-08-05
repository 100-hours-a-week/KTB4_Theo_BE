package com.theo.community_api.image.controller;

import com.theo.community_api.auth.jwt.JwtTokenProvider;
import com.theo.community_api.auth.security.CustomUserDetails;
import com.theo.community_api.auth.security.CustomUserDetailsService;
import com.theo.community_api.auth.security.JwtAccessDeniedHandler;
import com.theo.community_api.auth.security.JwtAuthenticationEntryPoint;
import com.theo.community_api.auth.security.JwtAuthenticationFilter;
import com.theo.community_api.common.config.SecurityConfig;
import com.theo.community_api.image.domain.ImageCategory;
import com.theo.community_api.image.service.ImageService;
import com.theo.community_api.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
@DisplayName("이미지 컨트롤러 테스트")
class ImageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ImageService imageService;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("인증된 사용자가 이미지를 업로드하면 객체 키와 201을 반환한다")
    void uploadImages_success() throws Exception {
        // given
        MockMultipartFile file = jpegFile();
        CustomUserDetails userDetails = createUserDetails(15L);

        given(imageService.upload(
                eq(15L),
                eq(ImageCategory.POST),
                anyList()
        )).willReturn(List.of("local/posts/15/image.jpg"));

        // when
        ResultActions result = mockMvc.perform(
                multipart("/images")
                        .file(file)
                        .param("category", "POST")
                        .with(user(userDetails))
        );

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("image_upload_success"))
                .andExpect(jsonPath("$.data.imageKeys[0]")
                        .value("local/posts/15/image.jpg"));
    }

    @Test
    @DisplayName("인증되지 않은 이미지 업로드 요청은 401을 반환한다")
    void uploadImages_unauthenticated() throws Exception {
        // given
        MockMultipartFile file = jpegFile();

        // when
        ResultActions result = mockMvc.perform(
                multipart("/images")
                        .file(file)
                        .param("category", "POST")
        );

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("access_token_required"));

        verifyNoInteractions(imageService);
    }

    @Test
    @DisplayName("이미지 category가 누락되면 400을 반환한다")
    void uploadImages_missingCategory() throws Exception {
        // given
        MockMultipartFile file = jpegFile();
        CustomUserDetails userDetails = createUserDetails(15L);

        // when
        ResultActions result = mockMvc.perform(
                multipart("/images")
                        .file(file)
                        .with(user(userDetails))
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid_request"));

        verifyNoInteractions(imageService);
    }

    @Test
    @DisplayName("지원하지 않는 이미지 category이면 400을 반환한다")
    void uploadImages_invalidCategory() throws Exception {
        // given
        MockMultipartFile file = jpegFile();
        CustomUserDetails userDetails = createUserDetails(15L);

        // when
        ResultActions result = mockMvc.perform(
                multipart("/images")
                        .file(file)
                        .param("category", "INVALID")
                        .with(user(userDetails))
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid_request"));

        verifyNoInteractions(imageService);
    }

    @Test
    @DisplayName("multipart 파일이 누락되면 400을 반환한다")
    void uploadImages_missingFiles() throws Exception {
        // given
        CustomUserDetails userDetails = createUserDetails(15L);

        // when
        ResultActions result = mockMvc.perform(
                multipart("/images")
                        .param("category", "POST")
                        .with(user(userDetails))
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid_request"));

        verifyNoInteractions(imageService);
    }

    @Test
    @DisplayName("multipart가 아닌 Content-Type 요청은 415를 반환한다")
    void uploadImages_unsupportedMediaType() throws Exception {
        // given
        CustomUserDetails userDetails = createUserDetails(15L);

        // when
        ResultActions result = mockMvc.perform(
                post("/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(userDetails))
        );

        // then
        result.andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("unsupported_media_type"));

        verifyNoInteractions(imageService);
    }

    private MockMultipartFile jpegFile() {
        return new MockMultipartFile(
                "files",
                "image.jpg",
                "image/jpeg",
                new byte[]{
                        (byte) 0xFF,
                        (byte) 0xD8,
                        (byte) 0xFF
                }
        );
    }

    private CustomUserDetails createUserDetails(Long userId) {
        User user = new User(
                "user@example.com",
                "encoded-password",
                "사용자",
                null
        );
        ReflectionTestUtils.setField(user, "id", userId);
        return CustomUserDetails.from(user);
    }
}
