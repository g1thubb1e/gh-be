package com.ssafy.githubble.domain.github.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryEntity;
import com.ssafy.githubble.domain.github.domain.GitHubRepositorySummary;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryTechStackMapping;
import com.ssafy.githubble.domain.github.dto.response.GitDiagramResponse;
import com.ssafy.githubble.domain.github.dto.response.GitHubRepoSummaryResponse;
import com.ssafy.githubble.domain.github.dto.response.GithubRepoTechStackResponse;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryRepository;
import com.ssafy.githubble.domain.github.repository.GitHubRepositorySummaryRepository;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryTechStackMappingRepository;
import com.ssafy.githubble.domain.profile.domain.TechStack;
import com.ssafy.githubble.domain.profile.service.S3PresignedUrlService;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import com.ssafy.githubble.global.properties.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitHubRepositoryService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final GitHubRepositorySummaryRepository gitHubRepositorySummaryRepository;
    private final GitHubRepositoryTechStackMappingRepository gitHubRepositoryTechStackMappingRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    public GitDiagramResponse getDiagram(String owner, String repo) {
        String key = s3Properties.getArtifactPrefix() + "/" + owner + "/" + repo + ".json";

        try {
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build());

            String content = objectBytes.asString(StandardCharsets.UTF_8);
            return objectMapper.readValue(content, GitDiagramResponse.class);
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "다이어그램 파일이 없습니다.");
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "다어이그램 파일 형식이 올바르지 않습니다.");
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "S3에 문제가 발생하였습니다.");
        }
    }

    public GitHubRepoSummaryResponse getSummary(String owner, String repo) {
        GitHubRepositoryEntity repository = gitHubRepositoryRepository
                .findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase(owner, repo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "정보가 존재하지 않는 레포지토리입니다."));

        GitHubRepositorySummary summary = gitHubRepositorySummaryRepository
                .findFirstByRepositoryRepoIdOrderByCreatedAtDesc(repository.getRepoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "저장된 레포지토리 설명이 없습니다."));

        return new GitHubRepoSummaryResponse(
                repository.getOwnerLogin(),
                repository.getRepoName(),
                summary.getExplanation()
        );
    }

    public List<GithubRepoTechStackResponse> getTechStack(String owner, String repo) {
        GitHubRepositoryEntity repository = gitHubRepositoryRepository
                .findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase(owner, repo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "정보가 존재하지 않는 레포지토리입니다."));

        return gitHubRepositoryTechStackMappingRepository.findByRepositoryRepoId(repository.getRepoId())
                .stream()
                .map(GitHubRepositoryTechStackMapping::getTechStack)
                .map(this::toTechStackResponse)
                .toList();
    }

    private GithubRepoTechStackResponse toTechStackResponse(TechStack techStack) {
        String icon = techStack.getIcon();
        String iconUrl = (icon == null) ? null : s3PresignedUrlService.createGetUrl(icon);
        return GithubRepoTechStackResponse.from(techStack, iconUrl);
    }
}
