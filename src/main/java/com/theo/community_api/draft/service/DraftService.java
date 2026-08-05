package com.theo.community_api.draft.service;

import com.theo.community_api.common.exception.*;
import com.theo.community_api.draft.domain.Draft;
import com.theo.community_api.draft.domain.DraftImage;
import com.theo.community_api.draft.dto.DraftCreateRequest;
import com.theo.community_api.draft.dto.DraftImageResponse;
import com.theo.community_api.draft.dto.DraftResponse;
import com.theo.community_api.draft.dto.DraftSummaryResponse;
import com.theo.community_api.draft.dto.DraftUpdateRequest;
import com.theo.community_api.draft.repository.DraftImageRepository;
import com.theo.community_api.draft.repository.DraftRepository;
import com.theo.community_api.image.domain.ImageCategory;
import com.theo.community_api.image.service.ImageService;
import com.theo.community_api.image.url.ImageUrlResolver;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.post.domain.PostImage;
import com.theo.community_api.post.repository.PostImageRepository;
import com.theo.community_api.post.repository.PostRepository;
import com.theo.community_api.user.domain.User;
import com.theo.community_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DraftService {

    private final DraftRepository draftRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final DraftImageRepository draftImageRepository;
    private final PostImageRepository postImageRepository;
    private final ImageUrlResolver imageUrlResolver;
    private final ImageService imageService;

    // 임시글 생성
    @Transactional
    public DraftResponse createDraft(
            Long loginUserId,
            DraftCreateRequest request,
            List<MultipartFile> images
    ) {
        // 제목 또는 내용 비어있는 경우
        if (isEmptyDraft(
                request.getTitle(),
                request.getContent(),
                List.of(),
                images
        )) {
            throw new BusinessException(ErrorCode.EMPTY_DRAFT_CONTENT);
        }

        User user = userRepository.findById(loginUserId)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_REQUEST);
        }

        Draft draft = new Draft(user,request.getTitle(), request.getContent());

        Draft savedDraft = draftRepository.save(draft);

        List<String> imageKeys = uploadDraftImages(
                loginUserId,
                images
        );

        imageService.deleteOnRollback(imageKeys);
        saveDraftImages(savedDraft, imageKeys);

        return toDraftResponse(savedDraft);
    }

    // 내 임시글 목록 조회
    public List<DraftSummaryResponse> readDraftList(Long loginUserId) {
        List<Draft> drafts = draftRepository.findAllByUserIdOrderByUpdatedAtDesc(loginUserId);

        List<DraftSummaryResponse> responses = new ArrayList<>();

        for (Draft draft : drafts) {
            responses.add(DraftSummaryResponse.from(draft));
        }

        return responses;
    }


    // 임시글 단건 조회
    public DraftResponse readDraft(Long loginUserId, Long draftId) {

        Draft draft = draftRepository.findByIdAndUserId(draftId, loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));

        return toDraftResponse(draft);
    }

    // 임시글 덮어쓰기
    @Transactional
    public DraftResponse updateDraft(
            Long loginUserId,
            Long draftId,
            DraftUpdateRequest request,
            List<MultipartFile> newImages
    ) {
        if (isEmptyDraft(
                request.getTitle(),
                request.getContent(),
                request.getRetainedImageIds(),
                newImages
        )) {
            throw new BusinessException(ErrorCode.EMPTY_DRAFT_CONTENT);
        }

        Draft draft = draftRepository.findByIdAndUserId(draftId, loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));

        List<DraftImage> existingImages =
                draftImageRepository.findAllByDraftIdOrderByImageOrderAsc(draft.getId());

        List<Long> retainedImageIds = normalizeRetainedImageIds(
                request.getRetainedImageIds()
        );
        List<DraftImage> retainedImages = findRetainedDraftImages(
                existingImages,
                retainedImageIds
        );
        Set<Long> retainedIdSet = new HashSet<>(retainedImageIds);
        List<DraftImage> removedImages = existingImages.stream()
                .filter(image -> !retainedIdSet.contains(image.getId()))
                .toList();

        List<String> uploadedImageKeys = uploadDraftImages(
                loginUserId,
                newImages
        );

        imageService.deleteOnRollback(uploadedImageKeys);

        List<String> removedImageKeys = removedImages.stream()
                .map(DraftImage::getImageKey)
                .toList();

        draft.update(request.getTitle(), request.getContent());

        int imageOrder = 1;
        for (DraftImage retainedImage : retainedImages) {
            retainedImage.updateOrder(imageOrder++);
        }

        draftImageRepository.deleteAll(removedImages);
        saveDraftImages(draft, uploadedImageKeys, imageOrder);
        imageService.deleteAfterCommit(removedImageKeys);

        return toDraftResponse(draft);
    }

    // 임시글 삭제
    @Transactional
    public void deleteDraft(Long loginUserId, Long draftId) {

        Draft draft = draftRepository.findByIdAndUserId(draftId, loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));

        List<DraftImage> draftImages = draftImageRepository
                .findAllByDraftIdOrderByImageOrderAsc(draft.getId());

        List<String> imageKeys = draftImages.stream()
                .map(DraftImage::getImageKey)
                .toList();

        draftImageRepository.deleteAll(draftImages);
        draftRepository.delete(draft);
        imageService.deleteAfterCommit(imageKeys);
    }

    // 임시글 발행
    @Transactional
    public Long publishDraft(Long loginUserId, Long draftId) {
        Draft draft = draftRepository.findByIdAndUserId(draftId, loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));

        if (isInvalidPublishDraft(draft.getTitle(), draft.getContent())) {
            throw new BusinessException(ErrorCode.DRAFT_PUBLISH_REQUIRED_TITLE_AND_CONTENT);
        }

        Post post = new Post(
                draft.getUser(),
                draft.getTitle(),
                draft.getContent()
        );

        // 저장할 임시글
        Post savedPost = postRepository.save(post);
        // 저장할 임시글 사진들
        List<DraftImage> draftImages = draftImageRepository.findAllByDraftIdOrderByImageOrderAsc(draft.getId());

        for (DraftImage draftImage : draftImages) {
                PostImage postImage = new PostImage(
                    savedPost,
                    draftImage.getImageKey(),
                    draftImage.getImageOrder()
                );

                postImageRepository.save(postImage);
        }

        // 발행된 Key는 게시글이 이어서 사용하므로 임시글 이미지 DB 관계만 삭제한다.
        draftImageRepository.deleteAllByDraftId(draft.getId());

        // 기존 임시글 삭제
        draftRepository.delete(draft);

        return savedPost.getId();
    }

    private void saveDraftImages(Draft draft, List<String> imageKeys) {
        saveDraftImages(draft, imageKeys, 1);
    }

    private void saveDraftImages(Draft draft, List<String> imageKeys, int startOrder) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return;
        }

        List<DraftImage> draftImages = new ArrayList<>();

        for (int i = 0; i < imageKeys.size(); i++) {
            String imageKey = imageKeys.get(i);

            if (isBlank(imageKey)) {
                continue;
            }

            DraftImage draftImage = new DraftImage(
                    draft,
                    imageKey,
                    startOrder + i
            );

            draftImages.add(draftImage);
        }

        draftImageRepository.saveAll(draftImages);
    }

    private List<String> uploadDraftImages(
            Long userId,
            List<MultipartFile> images
    ) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        return imageService.upload(
                userId,
                ImageCategory.POST,
                images
        );
    }

    private List<Long> normalizeRetainedImageIds(List<Long> imageIds) {
        if (imageIds == null) {
            return List.of();
        }

        Set<Long> uniqueIds = new HashSet<>();
        for (Long imageId : imageIds) {
            if (imageId == null || !uniqueIds.add(imageId)) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE_ID);
            }
        }

        return List.copyOf(imageIds);
    }

    private List<DraftImage> findRetainedDraftImages(
            List<DraftImage> existingImages,
            List<Long> retainedImageIds
    ) {
        Map<Long, DraftImage> existingImagesById = new HashMap<>();
        for (DraftImage existingImage : existingImages) {
            existingImagesById.put(existingImage.getId(), existingImage);
        }

        List<DraftImage> retainedImages = new ArrayList<>();
        for (Long retainedImageId : retainedImageIds) {
            DraftImage retainedImage = existingImagesById.get(retainedImageId);
            if (retainedImage == null) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE_ID);
            }
            retainedImages.add(retainedImage);
        }

        return retainedImages;
    }

    private DraftResponse toDraftResponse(Draft draft) {
        List<DraftImage> draftImages =
                draftImageRepository.findAllByDraftIdOrderByImageOrderAsc(draft.getId());

        List<DraftImageResponse> imageResponses = new ArrayList<>();

        for (DraftImage draftImage : draftImages) {
            String imageUrl = imageUrlResolver.resolve(
                    draftImage.getImageKey()
            );

            imageResponses.add(
                    DraftImageResponse.from(draftImage, imageUrl)
            );
        }

        return DraftResponse.from(draft, imageResponses);
    }

    private boolean isEmptyDraft(
            String title,
            String content,
            List<Long> retainedImageIds,
            List<MultipartFile> newImages
    ) {
        return isBlank(title)
                && isBlank(content)
                && isEmptyImageIds(retainedImageIds)
                && (newImages == null || newImages.isEmpty());
    }

    private boolean isEmptyImageIds(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return true;
        }

        for (Long imageId : imageIds) {
            if (imageId != null) {
                return false;
            }
        }

        return true;
    }

    private boolean isInvalidPublishDraft(String title, String content){
        return isBlank(title) || isBlank(content);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
