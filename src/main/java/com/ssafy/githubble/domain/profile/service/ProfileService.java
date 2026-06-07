package com.ssafy.githubble.domain.profile.service;

import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.auth.repository.UserRepository;
import com.ssafy.githubble.domain.github.support.GithubAccessTokenProvider;
import com.ssafy.githubble.domain.profile.client.GithubContributionClient;
import com.ssafy.githubble.domain.profile.client.GithubProfileClient;
import com.ssafy.githubble.domain.profile.domain.GithubProfile;
import com.ssafy.githubble.domain.profile.dto.GithubUserApiResponse;
import com.ssafy.githubble.domain.profile.dto.ProfileResponse;
import com.ssafy.githubble.domain.profile.repository.ProfileRepository;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;

@Service
public class ProfileService {

    private static final Duration PROFILE_CACHE_TTL = Duration.ofHours(24);

    private final GithubProfileClient githubProfileClient;
    private final GithubContributionClient githubContributionClient;
    private final GithubAccessTokenProvider githubAccessTokenProvider;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public ProfileService(
            GithubProfileClient githubProfileClient,
            GithubContributionClient githubContributionClient,
            GithubAccessTokenProvider githubAccessTokenProvider,
            ProfileRepository profileRepository,
            UserRepository userRepository,
            @Qualifier("jpaTransactionTemplate") TransactionTemplate transactionTemplate
    ) {
        this.githubProfileClient = githubProfileClient;
        this.githubContributionClient = githubContributionClient;
        this.githubAccessTokenProvider = githubAccessTokenProvider;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = transactionTemplate;
    }

    // 나의 프로필 조회
    public ProfileResponse getMyProfile(Long appuserId) {
        User user = userRepository.findById(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비활성화된 계정입니다.");
        }

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "GitHub 재로그인이 필요합니다.");
        }
        return getServiceUserProfile(appuserId, user);
    }

    // 사용자 프로필 조회
    public ProfileResponse getProfile(Long requesterAppuserId, String username) {
        return userRepository.findByUsernameAndIsDeletedFalse(username)
                .map(user -> getServiceUserProfile(requesterAppuserId, user))
                .orElseGet(() -> getExternalProfile(requesterAppuserId, username));
    }

    // 서비스 사용자 프로필 조회
    private ProfileResponse getServiceUserProfile(Long requesterAppuserId, User profileUser) {
        return profileRepository.findByUsername(profileUser.getUsername())
                .filter(this::isFresh)
                .map(ProfileResponse::from)
                .orElseGet(() -> refreshProfile(requesterAppuserId, profileUser));
    }

    // 24시간이 지나면 프로필 정보 업데이트
    private boolean isFresh(GithubProfile profile) {
        LocalDateTime updatedAt = profile.getUpdatedAt();
        return updatedAt != null && updatedAt.isAfter(LocalDateTime.now().minus(PROFILE_CACHE_TTL));
    }

    // 프로필 업데이트: 외부 API 호출 + 저장(TX)
    private ProfileResponse refreshProfile(Long requesterAppuserId, User profileUser) {
        String githubAccessToken = githubAccessTokenProvider.getGithubAccessToken(requesterAppuserId);

        GithubUserApiResponse githubUser = githubProfileClient.getUser(githubAccessToken, profileUser.getUsername());
        int commitCountThisYear = fetchCommitCountThisYear(githubAccessToken, profileUser.getUsername());

        return saveOrUpdateProfile(profileUser, githubUser, commitCountThisYear);
    }


    private ProfileResponse saveOrUpdateProfile(User profileUser, GithubUserApiResponse githubUser, int commitCountThisYear) {
        return transactionTemplate.execute(status -> {
            GithubProfile profile = profileRepository.findByUsername(githubUser.login())
                    .orElseGet(() -> GithubProfile.builder().build());

            profile.updateGithubProfile(
                    profileUser.getAppUserId(),
                    githubUser.login(),
                    githubUser.name(),
                    githubUser.avatarUrl(),
                    githubUser.type(),
                    githubUser.company(),
                    githubUser.blog(),
                    githubUser.location(),
                    githubUser.email(),
                    githubUser.bio(),
                    githubUser.publicRepos(),
                    githubUser.createdAt(),
                    commitCountThisYear
            );

            return ProfileResponse.from(profileRepository.save(profile));
        });
    }

    // 서비스 사용자가 아닌 프로필 조회
    private ProfileResponse getExternalProfile(Long requesterAppuserId, String username) {
        String githubAccessToken = githubAccessTokenProvider.getGithubAccessToken(requesterAppuserId);

        GithubUserApiResponse githubUser = githubProfileClient.getUser(githubAccessToken, username);
        int commitCountThisYear = fetchCommitCountThisYear(githubAccessToken, username);

        return ProfileResponse.from(githubUser, commitCountThisYear);
    }

    private int fetchCommitCountThisYear(String githubAccessToken, String username) {
        OffsetDateTime from = startOfThisYear();
        OffsetDateTime to = from.plusYears(1);

        return githubContributionClient.getCommitCount(
                githubAccessToken,
                username,
                from,
                to
        );
    }

    private OffsetDateTime startOfThisYear() {
        LocalDate firstDayOfYear = LocalDate.now(ZoneOffset.UTC)
                .withDayOfYear(1);

        return firstDayOfYear
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);
    }
}
