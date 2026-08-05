package com.theo.community_api.image.controller;

import com.theo.community_api.auth.security.CustomUserDetails;
import com.theo.community_api.common.ApiResponse;
import com.theo.community_api.image.domain.ImageCategory;
import com.theo.community_api.image.dto.ImageUploadResponse;
import com.theo.community_api.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponse>>
    uploadImages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam ImageCategory category,
            @RequestParam("files") List<MultipartFile> files
    ) {
        List<String> imageKeys = imageService.upload(
                userDetails.getUserId(), category, files
        );

        ImageUploadResponse response = new ImageUploadResponse(imageKeys);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        "image_upload_success",
                        response
                ));
    }
}