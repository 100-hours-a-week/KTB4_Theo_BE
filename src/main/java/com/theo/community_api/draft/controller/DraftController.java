package com.theo.community_api.draft.controller;

import com.theo.community_api.auth.security.CustomUserDetails;
import com.theo.community_api.common.ApiResponse;
import com.theo.community_api.draft.dto.DraftCreateRequest;
import com.theo.community_api.draft.dto.DraftResponse;
import com.theo.community_api.draft.dto.DraftSummaryResponse;
import com.theo.community_api.draft.dto.DraftUpdateRequest;
import com.theo.community_api.draft.service.DraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/posts/draft")
@RequiredArgsConstructor
public class DraftController {
    private final DraftService draftService;

    // 임시글 생성
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DraftResponse>> craftDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("request") DraftCreateRequest request,
            @RequestPart(value = "images", required = false)
            List<MultipartFile> images
    ){
        DraftResponse response = draftService.createDraft(
                userDetails.getUserId(),
                request,
                images
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("draft_create_success",response));
    }

    // 내 임시글 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<DraftSummaryResponse>>> readMyDrafts(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<DraftSummaryResponse> response = draftService.readDraftList(userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.of("draft_list_read_success", response));
    }

    // 임시글 조회
    @GetMapping("/{draftId}")
    public ResponseEntity<ApiResponse<DraftResponse>> readDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long draftId
    ){
        DraftResponse response = draftService.readDraft(userDetails.getUserId(), draftId);
        return ResponseEntity.ok(ApiResponse.of("draft_read_success",response));
    }

    // 임시글 덮어쓰기
    @PutMapping(
            value = "/{draftId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<DraftResponse>> updateDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long draftId,
            @RequestPart("request") DraftUpdateRequest request,
            @RequestPart(value = "newImages", required = false)
            List<MultipartFile> newImages
    ){
        DraftResponse response = draftService.updateDraft(
                userDetails.getUserId(),
                draftId,
                request,
                newImages
        );
        return ResponseEntity.ok(ApiResponse.of("draft_update_success",response));
    }

    // 임시글 삭제
    @DeleteMapping("/{draftId}")
    public ResponseEntity<ApiResponse<Void>> deleteDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long draftId
    ){
        draftService.deleteDraft(userDetails.getUserId(), draftId);

        return ResponseEntity.ok(ApiResponse.of("draft_delete_success"));
    }

    // 임시글 발행
    @PostMapping("/{draftId}/publish")
    public ResponseEntity<ApiResponse<Long>> publishDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long draftId
    ) {
        Long postId = draftService.publishDraft(userDetails.getUserId(), draftId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("draft_publish_success", postId));
    }
}
