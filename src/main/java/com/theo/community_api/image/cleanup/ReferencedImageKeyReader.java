package com.theo.community_api.image.cleanup;

import com.theo.community_api.draft.repository.DraftImageRepository;
import com.theo.community_api.post.repository.PostImageRepository;
import com.theo.community_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ReferencedImageKeyReader {

    private final UserRepository userRepository;
    private final PostImageRepository postImageRepository;
    private final DraftImageRepository draftImageRepository;

    @Transactional(readOnly = true)
    public Set<String> readAll() {
        Set<String> referencedKeys = new HashSet<>();

        addValidKeys(
                referencedKeys,
                userRepository.findAllProfileImageKeys()
        );
        addValidKeys(
                referencedKeys,
                postImageRepository.findAllImageKeys()
        );
        addValidKeys(
                referencedKeys,
                draftImageRepository.findAllImageKeys()
        );

        return Set.copyOf(referencedKeys);
    }

    private void addValidKeys(
            Set<String> referencedKeys,
            List<String> imageKeys
    ) {
        imageKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .forEach(referencedKeys::add);
    }
}
