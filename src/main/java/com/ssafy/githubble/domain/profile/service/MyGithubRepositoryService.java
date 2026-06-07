package com.ssafy.githubble.domain.profile.service;

import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.auth.repository.UserRepository;
import com.ssafy.githubble.domain.github.support.GithubAccessTokenProvider;
import com.ssafy.githubble.domain.profile.client.GithubRepositoryListClient;
import com.ssafy.githubble.domain.profile.domain.GithubProfile;
import com.ssafy.githubble.domain.profile.domain.MyGithubRepository;
import com.ssafy.githubble.domain.profile.dto.GithubRepoResponse;
import com.ssafy.githubble.domain.profile.dto.RepoResponse;
import com.ssafy.githubble.domain.profile.repository.MyGithubRepositoryRepository;
import com.ssafy.githubble.domain.profile.repository.ProfileRepository;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class MyGithubRepositoryService {

    private static final int PER_PAGE = 100;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final MyGithubRepositoryRepository myGithubRepositoryRepository;
    private final GithubAccessTokenProvider githubAccessTokenProvider;
    private final GithubRepositoryListClient githubRepositoryClient;
    private final TransactionTemplate transactionTemplate;

    public MyGithubRepositoryService(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            MyGithubRepositoryRepository myGithubRepositoryRepository,
            GithubAccessTokenProvider githubAccessTokenProvider,
            GithubRepositoryListClient githubRepositoryClient,
            @Qualifier("jpaTransactionTemplate") TransactionTemplate transactionTemplate
    ) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.myGithubRepositoryRepository = myGithubRepositoryRepository;
        this.githubAccessTokenProvider = githubAccessTokenProvider;
        this.githubRepositoryClient = githubRepositoryClient;
        this.transactionTemplate = transactionTemplate;
    }

    // /api/v1/profiles/me/repositories 조회
    // 프로필에 저장해 둔 레포목록 조회 최신일보다 이후에 업데이트가 발생한 레포들의 목록만 조회
    public Page<RepoResponse> getMyRepositoryList(Long appuserId, Pageable pageable) {
        User me = userRepository.findById(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (Boolean.TRUE.equals(me.getIsDeleted())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비활성화된 계정입니다.");
        }
        if (me.getUsername() == null || me.getUsername().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "GitHub 재로그인이 필요합니다.");
        }

        GithubProfile profile = profileRepository.findByUsername(me.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "프로필이 없습니다."));

        String token = githubAccessTokenProvider.getGithubAccessToken(appuserId);

        // 마지막 동기화 시점 찾기
        OffsetDateTime lastSyncedAt = profile.getRepoUpdatedAt();
        // 조회할 레포 목록의 업데이트일 지정(업데이트일이 since인 레포부터 가져옴)
        OffsetDateTime since = (lastSyncedAt == null) ? null : lastSyncedAt.minusMinutes(3);

        int githubPage = 1;
        while (true) {
            List<GithubRepoResponse> repos = githubRepositoryClient.getMyRepositoryList(
                    token, since, PER_PAGE, githubPage
            );

            if (repos == null || repos.isEmpty()) {
                break;
            }

            // DB와 레포목록 동기화
            syncRepos(profile, repos);

            if (repos.size() < PER_PAGE) {
                break;
            }
            githubPage++;
        }
        // 페이지 동기화 완료 시점 기록
        profile.updateRepoUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        profileRepository.save(profile);

        return myGithubRepositoryRepository
                .findByProfile(profile, pageable)
                .map(this::toResponse);
    }

    // 레포 목록 1페이지 DB와 동기화
    private void syncRepos(GithubProfile profile, List<GithubRepoResponse> repos) {
        transactionTemplate.executeWithoutResult(status -> {
            for (GithubRepoResponse repo : repos) {
                myGithubRepositoryRepository.findByProfileAndRepoName(profile, repo.name())
                        .ifPresentOrElse(
                                existing -> existing.updateFromGithub(
                                                repo.name(),
                                                repo.description(),
                                                repo.htmlUrl(),
                                                repo.language(),
                                                repo.stargazersCount(),
                                                repo.updatedAt()
                                        ),
                                // 없으면 insert
                                () -> myGithubRepositoryRepository.save(
                                        MyGithubRepository.builder()
                                                .profile(profile)
                                                .username(repo.owner() == null ? profile.getUsername() : repo.owner().login())
                                                .repoName(repo.name())
                                                .description(repo.description())
                                                .htmlUrl(repo.htmlUrl())
                                                .language(repo.language())
                                                .stargazerCnt(repo.stargazersCount())
                                                .updatedAt(repo.updatedAt())
                                                .createdAt(repo.createdAt())
                                                .build()
                                )
                        );
            }
        });
    }

    // 프론트 응답 주기
    private RepoResponse toResponse(MyGithubRepository repo) {
        return new RepoResponse(
                repo.getUsername(),
                repo.getRepoName(),
                repo.getDescription(),
                repo.getHtmlUrl(),
                repo.getLanguage(),
                repo.getStargazerCnt() == null ? 0 : repo.getStargazerCnt(),
                repo.getCreatedAt(),
                repo.getUpdatedAt() == null ? null : repo.getUpdatedAt().withOffsetSameInstant(ZoneOffset.UTC)
        );
    }
}
