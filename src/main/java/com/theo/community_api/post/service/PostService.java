package com.theo.community_api.post.service;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.comment.repository.CommentRepository;
import com.theo.community_api.common.exception.*;
import com.theo.community_api.image.domain.ImageCategory;
import com.theo.community_api.image.service.ImageService;
import com.theo.community_api.image.url.ImageUrlResolver;
import com.theo.community_api.image.validation.ImageKeyValidator;
import com.theo.community_api.notification.service.NotificationService;
import com.theo.community_api.post.domain.*;
import com.theo.community_api.post.dto.*;
import com.theo.community_api.post.repository.*;
import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.reply.repository.ReplyRepository;
import com.theo.community_api.user.domain.User;
import com.theo.community_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final ReplyRepository replyRepository;
    private final PostImageRepository postImageRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostViewRepository postViewRepository;
    private final PostRevisionRepository postRevisionRepository;
    private final NotificationService notificationService;
    private final ImageUrlResolver imageUrlResolver;
    private final ImageKeyValidator imageKeyValidator;
    private final ImageService imageService;

    // 게시글 추가
    @Transactional
    public Long createPost(
            Long loginUserId,
            PostCreateRequest request,
            List<MultipartFile> images
    ) {
        User user = userRepository.findById(loginUserId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        if (user.isDeleted()) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED_REQUEST
            );
        }

        Post post = new Post(
                user,
                request.getTitle(),
                request.getContent()
        );

        Post savedPost = postRepository.save(post);

        List<String> imageKeys = uploadPostImages(
                loginUserId,
                images
        );

        // 이후 DB 저장이나 커밋이 실패하면 업로드된 S3 객체 삭제
        imageService.deleteOnRollback(imageKeys);

        savePostImages(savedPost, imageKeys);

        return savedPost.getId();
    }

    // 게시글 목록 조회
    public PostListResponse readPostList(Long lastPostId, int size) {
        PageRequest pageRequest = PageRequest.of(0, size + 1);

        // postId 기준 내림차순 정렬 (최신 게시물부터 나오도록)
        List<Post> findPosts;

        if(lastPostId == null){
            findPosts = postRepository.findFirstPage(pageRequest);
        }else{
            findPosts = postRepository.findNextPage(lastPostId, pageRequest);
        }

        List<PostSummaryResponse> posts = new ArrayList<>();

        for(Post post : findPosts){
            User author = post.getUser();
            String profileImageUrl = resolveProfileImageUrl(author);

            posts.add(
                    PostSummaryResponse.from(
                            post,
                            author,
                            profileImageUrl
                    )
            );
        }

        boolean hasNext = false;

        // 뒤에 게시물들이 더 남아있으면 확인
        if (posts.size() > size) {
            hasNext = true;

            // 마지막 1개 제거
            posts.remove(posts.size() - 1);
        }

        // 다음에 조회할 첫 게시물 ID
        Long newLastPostId = null;

        if (!posts.isEmpty()) {
            newLastPostId = posts.get(posts.size() - 1).getPostId();
        }

        return new PostListResponse(posts, hasNext, newLastPostId);
    }

    // 게시글 상세 조회
    @Transactional
    public PostDetailResponse readPostDetail(Long loginUserId, Long postId) {
        Post post = postRepository.findByIdWithUser(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.isBlinded()) {
            throw new BusinessException(ErrorCode.POST_BLINDED);
        }

        // 조회수 증가 (같은 사용자는 24시간 이상 지나야 증가)
        increaseViewCountIfPossible(post, loginUserId);

        User postAuthor = post.getUser();

        List<Comment> comments = commentRepository.findAllByPostIdWithUser(postId);
        List<Reply> replies = replyRepository.findAllByPostIdWithUserAndComment(postId);

        Map<Long, List<Reply>> repliesByCommentId = new HashMap<>();

        List<PostCommentResponse> commentResponses = new ArrayList<>();

        for (Reply reply : replies) {
            Long commentId = reply.getComment().getId();

            List<Reply> replyList = repliesByCommentId.get(commentId);

            if (replyList == null) {
                replyList = new ArrayList<>();
                repliesByCommentId.put(commentId, replyList);
            }

            replyList.add(reply);
        }

        for (Comment comment : comments) { // 댓글 리스트 가져오기
            User commentAuthor = comment.getUser();

            List<Reply> commentReplies = repliesByCommentId.getOrDefault(comment.getId(), new ArrayList<>());
            List<PostReplyResponse> replyResponses = new ArrayList<>();

            for (Reply reply : commentReplies) { // 대댓글 리스트 가져오기
                User replyAuthor = reply.getUser();
                String profileImageUrl = resolveProfileImageUrl(replyAuthor);

                replyResponses.add(
                        PostReplyResponse.from(
                                reply,
                                replyAuthor,
                                loginUserId,
                                profileImageUrl
                        )
                );
            }

            // 부모 댓글이 삭제되고, 대댓글도 없다면 가져오지 않도록 한다.
            if(comment.isDeleted() && replyResponses.isEmpty()){
                continue;
            }

            String profileImageUrl = resolveProfileImageUrl(commentAuthor);

            commentResponses.add(
                    PostCommentResponse.from(
                            comment,
                            commentAuthor,
                            loginUserId,
                            profileImageUrl,
                            replyResponses
                    )
            );
        }

        // 이미지 처리
        List<PostImage> postImages = postImageRepository.findAllByPost_IdOrderByImageOrderAsc(postId);

        List<String> imageUrls = new ArrayList<>();

        for (PostImage postImage : postImages) {
            String imageUrl = imageUrlResolver.resolve(postImage.getImageKey());

            imageUrls.add(imageUrl);
        }

        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, loginUserId);

        String authorProfileImageUrl = resolveProfileImageUrl(postAuthor);

        return PostDetailResponse.from(
                post,
                postAuthor,
                loginUserId,
                liked,
                authorProfileImageUrl,
                imageUrls,
                commentResponses
        );
    }

    // 게시글 수정
    @Transactional
    public PostUpdateResponse updatePost(
            Long loginUserId,
            Long postId,
            PostUpdateRequest request,
            List<MultipartFile> newImages
    ) {
        Post post = postRepository.findByIdWithUser(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUser().getId().equals(loginUserId)) {
            throw new BusinessException(ErrorCode.POST_MODIFY_FORBIDDEN);
        }

        List<PostImage> existingImages =
                postImageRepository.findAllByPost_IdOrderByImageOrderAsc(postId);

        List<String> existingImageKeys = existingImages.stream()
                .map(PostImage::getImageKey)
                .toList();

        List<String> retainedImageKeys = validatePostImageKeys(
                request.getRetainedImageKeys(),
                loginUserId
        );

        validateRetainedImageKeys(
                existingImageKeys,
                retainedImageKeys
        );

        List<String> uploadedImageKeys = uploadPostImages(
                loginUserId,
                newImages
        );

        imageService.deleteOnRollback(uploadedImageKeys);

        List<String> finalImageKeys = new ArrayList<>(retainedImageKeys);
        finalImageKeys.addAll(uploadedImageKeys);

        List<String> removedImageKeys = findRemovedImageKeys(
                existingImageKeys,
                retainedImageKeys
        );

        // 게시글 수정 전 기존 제목/내용을 수정이력으로 저장
        PostRevision postRevision = PostRevision.from(post);
        postRevisionRepository.save(postRevision);

        // 게시글 현재 내용 수정
        post.update(request.getTitle(), request.getContent());

        // DB 이미지 관계는 요청 순서대로 다시 저장하되, 유지된 S3 객체는 삭제하지 않는다.
        postImageRepository.deleteAllByPostIdInBulk(postId);
        savePostImages(post, finalImageKeys);
        imageService.deleteAfterCommit(removedImageKeys);

        return new PostUpdateResponse(post.isEdited());
    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long loginUserId, Long postId) {
        Post post = postRepository.findByIdWithUser(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUser().getId().equals(loginUserId)) {
            throw new BusinessException(ErrorCode.POST_DELETE_FORBIDDEN);
        }

        List<String> imageKeys = postImageRepository
                .findAllByPost_IdOrderByImageOrderAsc(postId)
                .stream()
                .map(PostImage::getImageKey)
                .toList();

        // 게시글 삭제 시 기존 이미지 모두 삭제
        postImageRepository.deleteAllByPostIdInBulk(postId);
        post.delete();
        imageService.deleteAfterCommit(imageKeys);
    }

    private void savePostImages(Post post, List<String> imageKeys) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return;
        }

        List<PostImage> postImages = new ArrayList<>();

        int imageOrder = 1;

        for (String imageKey : imageKeys) {
            PostImage postImage = new PostImage(post, imageKey, imageOrder);
            postImages.add(postImage);
            imageOrder++;
        }

        postImageRepository.saveAll(postImages);
    }

    private List<String> validatePostImageKeys(
            List<String> imageKeys,
            Long userId
    ) {
        imageKeyValidator.validateAllForUser(
                imageKeys,
                ImageCategory.POST,
                userId
        );

        return imageKeys == null
                ? List.of()
                : List.copyOf(imageKeys);
    }

    private List<String> findRemovedImageKeys(
            List<String> existingImageKeys,
            List<String> newImageKeys
    ) {
        Set<String> retainedKeys = new HashSet<>(newImageKeys);

        return existingImageKeys.stream()
                .filter(imageKey -> !retainedKeys.contains(imageKey))
                .toList();
    }

    private void validateRetainedImageKeys(
            List<String> existingImageKeys,
            List<String> retainedImageKeys
    ) {
        if (!existingImageKeys.containsAll(retainedImageKeys)) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_KEY);
        }
    }

    // 게시글 좋아요 토글
    @Transactional
    public PostLikeResponse togglePostLike(Long loginUserId, Long postId) {
        Post post = postRepository.findByIdWithUserForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.isBlinded()) {
            throw new BusinessException(ErrorCode.POST_BLINDED);
        }

        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_REQUEST);
        }

        Optional<PostLike> optionalPostLike =
                postLikeRepository.findByPostIdAndUserId(postId, loginUserId);

        // 이미 좋아요 누른 상태에서 다시 누르면 이전 좋아요 이력 삭제, 좋아요 개수 감소, 알림 생성 하지않고 종료
        if (optionalPostLike.isPresent()) {
            postLikeRepository.delete(optionalPostLike.get());
            post.decreaseLikeCount();

            return new PostLikeResponse(false, post.getLikeCount());
        }

        PostLike postLike = new PostLike(post, user);
        postLikeRepository.save(postLike);
        post.increaseLikeCount();

        notificationService.createLikeNotification(
                post, user // 수신 게시물, 행위자 actor
        );

        return new PostLikeResponse(true, post.getLikeCount());
    }

    // 게시글 조회 이력증가 (조회 시점으로부터 24시간마다 가능)
    private void increaseViewCountIfPossible(Post post, Long loginUserId) {
        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();

        Optional<PostView> optionalPostView =
                postViewRepository.findByPostIdAndUserId(post.getId(), loginUserId);

        // 해당 게시글 조회이력이 비어있으면 새로운 이력 저장 및 조회수 증가
        if (optionalPostView.isEmpty()) {
            PostView postView = new PostView(post, user);
            postViewRepository.save(postView);
            post.increaseViewCount();
            return;
        }

        PostView postView = optionalPostView.get();

        // 현재 시간으로부터 이전 시간이 24시간이 지났다면 조회수 증가 및 viewedAt 갱신
        if (postView.canIncreaseViewCount(now)) {
            postView.updateViewedAt(now);
            post.increaseViewCount();
        }
    }

    private String resolveProfileImageUrl(User user) {
        if (user == null || user.isDeleted()) {
            return null;
        }

        return imageUrlResolver.resolve(
                user.getProfileImageKey()
        );
    }

    private List<String> uploadPostImages(
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
}
