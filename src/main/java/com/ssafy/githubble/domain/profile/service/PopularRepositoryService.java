package com.ssafy.githubble.domain.profile.service;

import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.auth.repository.UserRepository;
import com.ssafy.githubble.domain.github.support.GithubAccessTokenProvider;
import com.ssafy.githubble.domain.profile.client.GithubPopularRepositoryClient;
import com.ssafy.githubble.domain.profile.dto.GithubRepoResponse;
import com.ssafy.githubble.domain.profile.dto.RepoResponse;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PopularRepositoryService {

    private final UserRepository userRepository;
    private final GithubAccessTokenProvider githubAccessTokenProvider;
    private final GithubPopularRepositoryClient githubPopularRepositoryClient;

    // /api/v1/profiles/me/repositories/most-popular
    public RepoResponse getMyMostPopularRepository(Long appuserId) {
        User me = userRepository.findById(appuserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (Boolean.TRUE.equals(me.getIsDeleted())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비활성화된 계정입니다.");
        }

        if (me.getUsername() == null || me.getUsername().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "GitHub 재로그인이 필요합니다.");
        }

        String token = githubAccessTokenProvider.getGithubAccessToken(appuserId);

        GithubRepoResponse repo = githubPopularRepositoryClient.getMostPopularRepository(
                token,
                me.getUsername()
        );

        return new RepoResponse(
                repo.owner() == null ? me.getUsername() : repo.owner().login(),
                repo.name(),
                repo.description(),
                repo.htmlUrl(),
                repo.language(),
                repo.stargazersCount() == null ? 0 : repo.stargazersCount(),
                repo.createdAt(),
                repo.updatedAt()
        );
    }
}
