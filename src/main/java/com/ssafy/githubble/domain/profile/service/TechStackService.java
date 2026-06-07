package com.ssafy.githubble.domain.profile.service;

import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.auth.repository.UserRepository;
import com.ssafy.githubble.domain.profile.domain.GithubProfile;
import com.ssafy.githubble.domain.profile.domain.TechStack;
import com.ssafy.githubble.domain.profile.domain.TechStackMapping;
import com.ssafy.githubble.domain.profile.dto.TechStackResponse;
import com.ssafy.githubble.domain.profile.dto.UpdateMyTechStacksRequest;
import com.ssafy.githubble.domain.profile.repository.ProfileRepository;
import com.ssafy.githubble.domain.profile.repository.TechStackMappingRepository;
import com.ssafy.githubble.domain.profile.repository.TechStackRepository;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "jpaTransactionManager", readOnly = true)
public class TechStackService {
    private final TechStackRepository techStackRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final TechStackMappingRepository techStackMappingRepository;


    // 모든 기술 스택 조회
    public List<TechStackResponse> getAllTechStacks() {
        return techStackRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 나의 기술 스택 업데이트
    @Transactional(transactionManager = "jpaTransactionManager")
    public List<TechStackResponse> updateMyTechStacks(
            Long appuserId,
            UpdateMyTechStacksRequest request
    ) {
        User me = userRepository.findById(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (Boolean.TRUE.equals(me.getIsDeleted())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비활성화된 계정입니다.");
        }

        if (me.getUsername() == null || me.getUsername().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "GitHub 로그인이 필요합니다.");
        }

        GithubProfile profile = profileRepository.findByAppuserId(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "프로필이 없습니다."));

        List<UUID> techStackUuids = request.techStackUuids()
                .stream()
                .distinct()
                .toList();

        List<TechStack> techStacks = techStackRepository.findAllByTechstackUuidIn(techStackUuids);

        if (techStacks.size() != techStackUuids.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 기술스택이 포함되어 있습니다.");
        }

        techStackMappingRepository.deleteByProfile(profile);

        List<TechStackMapping> mappings = techStacks.stream()
                .map(techStack -> new TechStackMapping(profile, techStack))
                .toList();

        techStackMappingRepository.saveAll(mappings);

        return techStacks.stream()
                .map(this::toResponse)
                .toList();
    }

    // 나의 기술 스택 조회
    public List<TechStackResponse> getMyTechStacks(Long appuserId) {
        User me = userRepository.findById(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (Boolean.TRUE.equals(me.getIsDeleted())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비활성화된 계정입니다.");
        }

        if (me.getUsername() == null || me.getUsername().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "GitHub 로그인이 필요합니다.");
        }

        GithubProfile profile = profileRepository.findByAppuserId(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "프로필이 없습니다."));

        return techStackMappingRepository.findAllByProfile(profile)
                .stream()
                .map(TechStackMapping::getTechStack)
                .map(this::toResponse)
                .toList();
    }

    private TechStackResponse toResponse(TechStack techStack) {
        String icon = techStack.getIcon();
        String iconUrl = (icon == null) ? null : s3PresignedUrlService.createGetUrl(icon);
        return TechStackResponse.from(techStack, iconUrl);
    }
}
