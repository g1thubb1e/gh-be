package com.ssafy.githubble.domain.github.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.githubble.domain.github.client.GitHubApiClient;
import com.ssafy.githubble.domain.github.dto.external.GitHubContentApiResponse;
import com.ssafy.githubble.domain.github.dto.external.GitHubRepositoryApiResponse;
import com.ssafy.githubble.domain.github.dto.external.GitHubTreeApiResponse;
import com.ssafy.githubble.domain.github.dto.response.GitHubConfigFile;
import com.ssafy.githubble.domain.github.dto.response.GitHubFileContentResponse;
import com.ssafy.githubble.domain.github.dto.response.GitHubFileSearchResponse;
import com.ssafy.githubble.domain.github.dto.response.GitHubReadmeInfo;
import com.ssafy.githubble.domain.github.dto.response.GitHubTreeFile;
import com.ssafy.githubble.domain.github.dto.response.ParsedRepositoryResponse;
import com.ssafy.githubble.domain.github.domain.GitHubFileContentCache;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryEntity;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryParseCache;
import com.ssafy.githubble.domain.github.domain.GitHubRepositoryTreeCache;
import com.ssafy.githubble.domain.github.repository.GitHubFileContentCacheRepository;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryParseCacheRepository;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryRepository;
import com.ssafy.githubble.domain.github.repository.GitHubRepositoryTreeCacheRepository;
import com.ssafy.githubble.domain.github.support.GithubAccessTokenProvider;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class GitHubRepositoryParseService {

    private static final String DEPENDENCY = "DEPENDENCY";
    private static final String INFRA = "INFRA";
    private static final String CONFIG = "CONFIG";
    private static final long MAX_TEXT_CONTENT_SIZE = 1024L * 1024L;
    private static final long MAX_REPOSITORY_CACHE_SIZE = 1024L * 1024L;
    private static final long CACHE_TTL_HOURS = 24L;
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "gz", "tar", "jar", "war",
            "class", "exe", "dll", "so", "dylib", "mp4", "mov", "avi", "mp3", "wav", "woff", "woff2", "ttf"
    );

    private final GitHubApiClient gitHubApiClient;
    private final GithubAccessTokenProvider githubAccessTokenProvider;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final GitHubFileContentCacheRepository gitHubFileContentCacheRepository;
    private final GitHubRepositoryTreeCacheRepository gitHubRepositoryTreeCacheRepository;
    private final GitHubRepositoryParseCacheRepository gitHubRepositoryParseCacheRepository;
    private final ObjectMapper objectMapper;

    public GitHubRepositoryParseService(GitHubApiClient gitHubApiClient,
                                         GithubAccessTokenProvider githubAccessTokenProvider,
                                         GitHubRepositoryRepository gitHubRepositoryRepository,
                                         GitHubFileContentCacheRepository gitHubFileContentCacheRepository,
                                         GitHubRepositoryTreeCacheRepository gitHubRepositoryTreeCacheRepository,
                                         GitHubRepositoryParseCacheRepository gitHubRepositoryParseCacheRepository,
                                         ObjectMapper objectMapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.githubAccessTokenProvider = githubAccessTokenProvider;
        this.gitHubRepositoryRepository = gitHubRepositoryRepository;
        this.gitHubFileContentCacheRepository = gitHubFileContentCacheRepository;
        this.gitHubRepositoryTreeCacheRepository = gitHubRepositoryTreeCacheRepository;
        this.gitHubRepositoryParseCacheRepository = gitHubRepositoryParseCacheRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "jpaTransactionManager")
    @Retryable(
        retryFor = org.springframework.dao.DataIntegrityViolationException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public ParsedRepositoryResponse parse(Long appuserId, String owner, String repo) {
        log.info("repository parse started for {}/{}", owner, repo);
        validate(owner, repo);
        log.info("loading github access token for {}/{}", owner, repo);
        String githubAccessToken = getGithubAccessToken(appuserId);
        logParseEvent("load_token", owner, repo, null, "succeeded", "-");

        log.info("requesting repository metadata for {}/{}", owner, repo);
        GitHubRepositoryApiResponse repository = gitHubApiClient.getRepository(githubAccessToken, owner, repo);
        logParseEvent("repository_metadata", owner, repo, null, "succeeded", "defaultBranch=" + repository.defaultBranch());
        log.info("repository metadata loaded for {}/{}: defaultBranch={}", owner, repo, repository.defaultBranch());
        validatePublicRepository(repository);
        log.info("upserting repository entity for {}/{}", owner, repo);
        GitHubRepositoryEntity repositoryEntity = upsertRepository(owner, repo, repository);
        log.info("repository entity upserted for {}/{}: repoId={}", owner, repo, repositoryEntity.getRepoId());

        log.info("requesting language statistics for {}/{}", owner, repo);
        Map<String, Long> languages = gitHubApiClient.getLanguages(githubAccessToken, owner, repo);
        logParseEvent("language_statistics", owner, repo, null, "succeeded",
                "languageCount=" + (languages == null ? 0 : languages.size()));
        log.info("language statistics loaded for {}/{}: languageCount={}", owner, repo,
                languages == null ? 0 : languages.size());

        long openIssueAndPullRequestCount = repository.openIssuesCount();
        log.info("requesting open pull request count for {}/{}", owner, repo);
        long openPullRequestCount = gitHubApiClient.countOpenPullRequests(githubAccessToken, owner, repo);
        long openIssueCount = Math.max(0L, openIssueAndPullRequestCount - openPullRequestCount);
        logParseEvent("issue_pr_count", owner, repo, null, "succeeded",
                "issuesOnly=" + openIssueCount + " pullRequests=" + openPullRequestCount);
        log.info("issue/pr counts loaded for {}/{}: issuesOnly={}, pullRequests={}, combined={}",
                owner, repo, openIssueCount, openPullRequestCount, openIssueAndPullRequestCount);

        log.info("loading repository tree for {}/{} on ref={}", owner, repo, repository.defaultBranch());
        GitHubTreeApiResponse tree = getTreeWithCache(githubAccessToken, repositoryEntity, owner, repo, repository.defaultBranch());
        logParseEvent("tree_load", owner, repo, repository.defaultBranch(), "succeeded",
                "truncated=" + (tree != null && tree.truncated()));
        log.info("repository tree loaded for {}/{}: truncated={}", owner, repo, tree != null && tree.truncated());

        List<String> warnings = new ArrayList<>();
        log.info("loading readme for {}/{}", owner, repo);
        GitHubReadmeInfo readme = parseReadme(githubAccessToken, owner, repo, warnings);
        logParseEvent("readme_load", owner, repo, repository.defaultBranch(), "succeeded",
                "exists=" + (readme != null) + " warnings=" + warnings.size());
        log.info("readme processed for {}/{}: exists={}, warnings={}", owner, repo, readme != null, warnings.size());

        log.info("parsing tree files for {}/{}", owner, repo);
        List<GitHubTreeFile> treeFiles = parseTreeFiles(tree);
        log.info("tree files parsed for {}/{}: fileCount={}", owner, repo, treeFiles.size());

        log.info("extracting config files for {}/{}", owner, repo);
        List<GitHubConfigFile> configFiles = parseConfigFiles(treeFiles);
        logParseEvent("config_extract", owner, repo, repository.defaultBranch(), "succeeded",
                "configCount=" + configFiles.size());
        log.info("config files extracted for {}/{}: configCount={}", owner, repo, configFiles.size());
        logParseEvent("parse_completed", owner, repo, repository.defaultBranch(), "succeeded",
                "fileCount=" + treeFiles.size() + " configCount=" + configFiles.size());
        log.info("repository parse completed for {}/{}", owner, repo);

        saveParseCache(
                repositoryEntity,
                repository,
                languages,
                openIssueCount,
                openPullRequestCount,
                openIssueAndPullRequestCount,
                readme,
                warnings
        );

        return new ParsedRepositoryResponse(
                repositoryEntity.getRepoUuid(),
                owner,
                repo,
                repository.fullName(),
                repository.htmlUrl(),
                repository.description(),
                repository.defaultBranch(),
                repository.language(),
                repository.stargazersCount(),
                repository.forksCount(),
                openIssueCount,
                openPullRequestCount,
                openIssueAndPullRequestCount,
                languages == null ? Map.of() : languages,
                tree != null && tree.truncated(),
                treeFiles,
                readme,
                configFiles,
                warnings,
                LocalDateTime.now()
        );
    }

    @Transactional(transactionManager = "jpaTransactionManager")
    public GitHubFileContentResponse getFileContent(Long appuserId, String owner, String repo, String path, String ref) {
        validate(owner, repo);
        validatePath(path);
        Optional<GitHubRepositoryEntity> savedRepository = gitHubRepositoryRepository
                .findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase(owner, repo);
        String resolvedRef = savedRepository
                .map(GitHubRepositoryEntity::getDefaultBranch)
                .map(defaultBranch -> normalizeRef(ref, defaultBranch))
                .orElse(ref);

        Optional<GitHubFileContentCache> savedCache = Optional.empty();
        if (savedRepository.isPresent() && resolvedRef != null && !resolvedRef.isBlank()) {
            savedCache = gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(
                    savedRepository.get().getRepoId(),
                    resolvedRef,
                    path
            );
            if (savedCache.isPresent() && isFresh(savedCache.get().getFetchedAt())) {
                GitHubFileContentCache cache = savedCache.get();
                cache.touch();
                return toFileContentResponse(owner, repo, resolvedRef, cache, true);
            }
        }

        String githubAccessToken = getGithubAccessToken(appuserId);
        GitHubRepositoryApiResponse repository = gitHubApiClient.getRepository(githubAccessToken, owner, repo);
        validatePublicRepository(repository);
        GitHubRepositoryEntity repositoryEntity = upsertRepository(owner, repo, repository);
        resolvedRef = normalizeRef(resolvedRef, repository.defaultBranch());

        return resolveFileContent(
                githubAccessToken,
                owner,
                repo,
                path,
                resolvedRef,
                repositoryEntity,
                savedCache
        );
    }

    @Transactional(transactionManager = "jpaTransactionManager")
    public GitHubFileSearchResponse searchFiles(Long appuserId, String owner, String repo, String query, String ref) {
        validate(owner, repo);
        validatePath(query);
        String githubAccessToken = getGithubAccessToken(appuserId);
        GitHubRepositoryApiResponse repository = gitHubApiClient.getRepository(githubAccessToken, owner, repo);
        validatePublicRepository(repository);
        GitHubRepositoryEntity repositoryEntity = upsertRepository(owner, repo, repository);
        String resolvedRef = normalizeRef(ref, repository.defaultBranch());

        GitHubTreeApiResponse tree = getTreeWithCache(githubAccessToken, repositoryEntity, owner, repo, resolvedRef);
        List<GitHubTreeFile> matches = parseTreeFiles(tree).stream()
                .filter(file -> "blob".equals(file.type()))
                .filter(file -> matchesQuery(file.path(), query))
                .toList();

        GitHubFileContentResponse singleMatchContent = null;
        if (matches.size() == 1) {
            singleMatchContent = resolveFileContent(
                    githubAccessToken,
                    owner,
                    repo,
                    matches.get(0).path(),
                    resolvedRef,
                    repositoryEntity,
                    Optional.empty()
            );
        }

        return new GitHubFileSearchResponse(owner, repo, resolvedRef, query, matches, singleMatchContent);
    }

    private GitHubFileContentResponse resolveFileContent(String githubAccessToken, String owner, String repo, String path,
                                                         String resolvedRef, GitHubRepositoryEntity repositoryEntity,
                                                         Optional<GitHubFileContentCache> savedCache) {
        if (savedCache.isEmpty()) {
            savedCache = gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(
                    repositoryEntity.getRepoId(),
                    resolvedRef,
                    path
            );
            if (savedCache.isPresent() && isFresh(savedCache.get().getFetchedAt())) {
                GitHubFileContentCache cache = savedCache.get();
                cache.touch();
                return toFileContentResponse(owner, repo, resolvedRef, cache, true);
            }
        }

        if (savedCache.isPresent() && !isFresh(savedCache.get().getFetchedAt())) {
            GitHubFileContentCache cache = savedCache.get();
            GitHubTreeApiResponse tree = getTreeWithCache(githubAccessToken, repositoryEntity, owner, repo, resolvedRef);
            if (hasSameSha(tree, path, cache)) {
                cache.markValidatedUnchanged();
                return toFileContentResponse(owner, repo, resolvedRef, cache, true);
            }
        }

        GitHubContentApiResponse content = gitHubApiClient.getContent(githubAccessToken, owner, repo, path, resolvedRef);
        validateFileContent(content);

        GitHubFileContentResponse response = buildFileContentResponse(owner, repo, resolvedRef, content, false);
        GitHubFileContentCache newCache = toCache(repositoryEntity, owner, repo, resolvedRef, response);
        evictOldCachesIfNeeded(repositoryEntity.getRepoId(), savedCache.orElse(null), newCache);
        if (savedCache.isPresent()) {
            savedCache.get().refresh(newCache);
        } else {
            gitHubFileContentCacheRepository.save(newCache);
        }
        return response;
    }

    private void validate(String owner, String repo) {
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void logParseEvent(String event, String owner, String repo, String ref, String status, String details) {
        log.info("github.parse.event event={} owner={} repo={} ref={} status={} details={}",
                event,
                owner,
                repo,
                ref == null || ref.isBlank() ? "-" : ref,
                status,
                details == null || details.isBlank() ? "-" : details);
    }

    private String cacheFailureDetails(RuntimeException e) {
        return "errorCode=github_cache_failed exceptionType=" + e.getClass().getName();
    }

    private boolean isFresh(LocalDateTime fetchedAt) {
        return fetchedAt != null && fetchedAt.isAfter(LocalDateTime.now().minusHours(CACHE_TTL_HOURS));
    }

    private void evictOldCachesIfNeeded(Long repoId, GitHubFileContentCache refreshingCache,
                                        GitHubFileContentCache newCache) {
        if (!Boolean.TRUE.equals(newCache.getContentAvailable()) || newCache.getSize() == null
                || newCache.getSize() <= 0 || newCache.getSize() > MAX_REPOSITORY_CACHE_SIZE) {
            return;
        }

        long cachedSize = gitHubFileContentCacheRepository.sumCachedContentSize(repoId);
        if (refreshingCache != null && Boolean.TRUE.equals(refreshingCache.getContentAvailable())
                && refreshingCache.getSize() != null) {
            cachedSize -= refreshingCache.getSize();
        }
        if (cachedSize + newCache.getSize() <= MAX_REPOSITORY_CACHE_SIZE) {
            return;
        }

        long currentSize = cachedSize;
        for (GitHubFileContentCache oldCache : gitHubFileContentCacheRepository
                .findByRepositoryRepoIdAndContentAvailableTrueOrderByLastAccessedAtAsc(repoId)) {
            if (currentSize + newCache.getSize() <= MAX_REPOSITORY_CACHE_SIZE) {
                break;
            }
            if (oldCache == refreshingCache) {
                continue;
            }
            currentSize -= oldCache.getSize() == null ? 0L : oldCache.getSize();
            gitHubFileContentCacheRepository.delete(oldCache);
        }
    }

    private GitHubTreeApiResponse getTreeWithCache(String githubAccessToken, GitHubRepositoryEntity repositoryEntity,
                                                   String owner, String repo, String ref) {
        Optional<GitHubRepositoryTreeCache> savedCache;
        try {
            savedCache = gitHubRepositoryTreeCacheRepository
                    .findByRepositoryRepoIdAndRef(repositoryEntity.getRepoId(), ref);
        } catch (RuntimeException e) {
            logParseEvent("tree_cache_load", owner, repo, ref, "failed", cacheFailureDetails(e));
            throw e;
        }
        if (savedCache.isPresent() && isFresh(savedCache.get().getFetchedAt())) {
            GitHubRepositoryTreeCache cache = savedCache.get();
            return deserializeTree(cache.getTreeJson());
        }

        GitHubTreeApiResponse tree = gitHubApiClient.getTree(githubAccessToken, owner, repo, ref);
        String treeJson = serializeTree(tree);
        if (savedCache.isPresent()) {
            savedCache.get().refresh(tree == null ? null : tree.sha(), tree != null && tree.truncated(), treeJson);
        } else {
            try {
                gitHubRepositoryTreeCacheRepository.save(new GitHubRepositoryTreeCache(
                        repositoryEntity,
                        ref,
                        tree == null ? null : tree.sha(),
                        tree != null && tree.truncated(),
                        treeJson
                ));
            } catch (RuntimeException e) {
                logParseEvent("tree_cache_save", owner, repo, ref, "failed", cacheFailureDetails(e));
                throw e;
            }
        }
        return tree;
    }

    private String serializeTree(GitHubTreeApiResponse tree) {
        try {
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "GitHub 파일 트리를 캐시에 저장하지 못했습니다.");
        }
    }

    private GitHubTreeApiResponse deserializeTree(String treeJson) {
        try {
            return objectMapper.readValue(treeJson, GitHubTreeApiResponse.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "GitHub 파일 트리 캐시를 읽지 못했습니다.");
        }
    }

    private boolean hasSameSha(GitHubTreeApiResponse tree, String path, GitHubFileContentCache cache) {
        if (tree == null || tree.tree() == null || cache.getSha() == null) {
            return false;
        }

        return tree.tree().stream()
                .filter(item -> "blob".equals(item.type()))
                .filter(item -> path.equals(item.path()))
                .map(GitHubTreeApiResponse.TreeItem::sha)
                .anyMatch(cache.getSha()::equals);
    }

    private void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String getGithubAccessToken(Long appuserId) {
        return githubAccessTokenProvider.getGithubAccessToken(appuserId);
    }

    private void validatePublicRepository(GitHubRepositoryApiResponse repository) {
        if (repository != null && repository.privateRepository()) {
            throw new BusinessException(ErrorCode.GITHUB_PRIVATE_REPOSITORY_NOT_SUPPORTED);
        }
    }

    private GitHubRepositoryEntity upsertRepository(String owner, String repo, GitHubRepositoryApiResponse repository) {
        Optional<GitHubRepositoryEntity> saved = repository.githubRepoId() == null
                ? Optional.empty()
                : gitHubRepositoryRepository.findByGithubRepoIdWithLock(repository.githubRepoId());
        GitHubRepositoryEntity entity = saved
                .or(() -> gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase(owner, repo))
                .orElseGet(() -> new GitHubRepositoryEntity(
                        repository.githubRepoId(),
                        owner,
                        repo,
                        repository.defaultBranch(),
                        repository.description(),
                        repository.privateRepository(),
                        repository.htmlUrl()
                ));
        entity.update(repository.defaultBranch(), repository.description(), repository.privateRepository(), repository.htmlUrl());
        return gitHubRepositoryRepository.save(entity);
    }

    private GitHubReadmeInfo parseReadme(String githubAccessToken, String owner, String repo, List<String> warnings) {
        Optional<GitHubContentApiResponse> readmeResponse = gitHubApiClient.getReadme(githubAccessToken, owner, repo);
        if (readmeResponse.isEmpty()) {
            warnings.add("README 파일을 찾을 수 없습니다.");
            return null;
        }

        GitHubContentApiResponse readme = readmeResponse.get();
        try {
            String content = readme.content() == null ? "" : readme.content().replace("\n", "");
            String decoded = new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
            return new GitHubReadmeInfo(readme.path(), decoded, readme.encoding());
        } catch (IllegalArgumentException e) {
            warnings.add("README 파일을 디코딩하지 못했습니다.");
            return new GitHubReadmeInfo(readme.path(), null, readme.encoding());
        }
    }

    private List<GitHubTreeFile> parseTreeFiles(GitHubTreeApiResponse tree) {
        if (tree == null || tree.tree() == null) {
            return List.of();
        }

        return tree.tree().stream()
                .map(item -> new GitHubTreeFile(
                        item.path(),
                        item.type(),
                        item.size(),
                        extractExtension(item.path()),
                        item.sha()
                ))
                .toList();
    }

    private void validateFileContent(GitHubContentApiResponse content) {
        if (content == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if ("dir".equals(content.type())) {
            throw new BusinessException(ErrorCode.GITHUB_CONTENT_IS_DIRECTORY);
        }
        if (!"file".equals(content.type())) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "일반 파일이 아닌 GitHub content입니다.");
        }
    }

    private GitHubFileContentResponse buildFileContentResponse(String owner, String repo, String ref,
                                                               GitHubContentApiResponse content, boolean cached) {
        String extension = extractExtension(content.path());
        boolean binary = isBinaryFile(content.path());
        boolean contentAvailable = false;
        String decodedContent = null;
        String unavailableReason = null;

        if (content.size() != null && content.size() > MAX_TEXT_CONTENT_SIZE) {
            unavailableReason = "FILE_TOO_LARGE";
        } else if (binary) {
            unavailableReason = "BINARY_FILE";
        } else if (!"base64".equalsIgnoreCase(content.encoding()) || content.content() == null || content.content().isBlank()) {
            unavailableReason = "CONTENT_NOT_PROVIDED";
        } else {
            try {
                byte[] decoded = Base64.getDecoder().decode(content.content().replace("\n", ""));
                decodedContent = new String(decoded, StandardCharsets.UTF_8);
                binary = containsNullByte(decoded);
                if (binary) {
                    decodedContent = null;
                    unavailableReason = "BINARY_FILE";
                } else {
                    contentAvailable = true;
                }
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "GitHub 파일 내용을 디코딩하지 못했습니다.");
            }
        }

        return new GitHubFileContentResponse(
                owner,
                repo,
                ref,
                content.type(),
                content.path(),
                content.name(),
                content.sha(),
                content.size(),
                content.encoding(),
                decodedContent,
                contentAvailable,
                binary,
                unavailableReason,
                content.htmlUrl(),
                cached
        );
    }

    private GitHubFileContentCache toCache(GitHubRepositoryEntity repository, String owner, String repo, String ref,
                                           GitHubFileContentResponse response) {
        return new GitHubFileContentCache(
                repository,
                ref,
                response.path(),
                response.name(),
                response.sha(),
                response.type(),
                response.size(),
                response.encoding(),
                response.content(),
                response.contentAvailable(),
                response.binary(),
                response.unavailableReason(),
                response.htmlUrl()
        );
    }

    private GitHubFileContentResponse toFileContentResponse(String owner, String repo, String ref,
                                                            GitHubFileContentCache cache, boolean cached) {
        return new GitHubFileContentResponse(
                owner,
                repo,
                ref,
                cache.getType(),
                cache.getPath(),
                cache.getFileName(),
                cache.getSha(),
                cache.getSize(),
                cache.getEncoding(),
                cache.getContent(),
                cache.getContentAvailable(),
                cache.getBinary(),
                cache.getUnavailableReason(),
                cache.getHtmlUrl(),
                cached
        );
    }

    private boolean matchesQuery(String path, String query) {
        if (query.contains("/")) {
            return path.equals(query);
        }
        return fileName(path).equals(query);
    }

    private String normalizeRef(String ref, String defaultBranch) {
        if (ref == null || ref.isBlank()) {
            return defaultBranch;
        }
        return ref;
    }

    private boolean isBinaryFile(String path) {
        String extension = extractExtension(path);
        return extension != null && BINARY_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    private boolean containsNullByte(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private List<GitHubConfigFile> parseConfigFiles(List<GitHubTreeFile> treeFiles) {
        return treeFiles.stream()
                .filter(file -> "blob".equals(file.type()))
                .map(file -> toConfigFile(file.path()))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<GitHubConfigFile> toConfigFile(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        String fileName = fileName(normalized);

        if (isDependencyFile(fileName)) {
            return Optional.of(new GitHubConfigFile(path, DEPENDENCY));
        }
        if (isInfraFile(normalized, fileName)) {
            return Optional.of(new GitHubConfigFile(path, INFRA));
        }
        if (isConfigFile(fileName)) {
            return Optional.of(new GitHubConfigFile(path, CONFIG));
        }
        return Optional.empty();
    }

    private boolean isDependencyFile(String fileName) {
        return List.of(
                "package.json",
                "package-lock.json",
                "yarn.lock",
                "pnpm-lock.yaml",
                "build.gradle",
                "build.gradle.kts",
                "settings.gradle",
                "pom.xml",
                "requirements.txt",
                "pyproject.toml",
                "poetry.lock",
                "go.mod",
                "cargo.toml"
        ).contains(fileName);
    }

    private boolean isInfraFile(String path, String fileName) {
        return fileName.equals("dockerfile")
                || fileName.equals("docker-compose.yml")
                || fileName.equals("docker-compose.yaml")
                || path.startsWith(".github/workflows/") && (path.endsWith(".yml") || path.endsWith(".yaml"))
                || path.startsWith("k8s/") && (path.endsWith(".yml") || path.endsWith(".yaml"));
    }

    private boolean isConfigFile(String fileName) {
        return fileName.equals("application.yml")
                || fileName.equals("application.yaml")
                || fileName.equals("application.properties")
                || fileName.equals("tsconfig.json")
                || fileName.startsWith("vite.config.")
                || fileName.startsWith("next.config.")
                || fileName.startsWith("eslint.config.");
    }

    private String fileName(String path) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return path;
        }
        return path.substring(index + 1);
    }

    private String extractExtension(String path) {
        if (path == null) {
            return null;
        }
        String fileName = fileName(path);
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(index + 1);
    }

    @Transactional(transactionManager = "jpaTransactionManager")
    public boolean hasFreshParseCache(String owner, String repo) {
        return gitHubRepositoryRepository
                .findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase(owner, repo)
                .map(repoEntity -> gitHubRepositoryParseCacheRepository
                        .findByRepositoryRepoId(repoEntity.getRepoId())
                        .isPresent())
                .orElse(false);
    }

    public ParsedRepositoryResponse getParsedFromCache(String owner, String repo) {
        GitHubRepositoryEntity repositoryEntity = gitHubRepositoryRepository
                .findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase(owner, repo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "저장소 정보를 찾을 수 없습니다."));

        GitHubRepositoryParseCache parseCache = gitHubRepositoryParseCacheRepository
                .findByRepositoryRepoId(repositoryEntity.getRepoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "파싱 캐시를 찾을 수 없습니다."));

        Map<String, Long> languages = deserializeLanguages(parseCache.getLanguagesJson());
        List<String> warnings = deserializeWarnings(parseCache.getWarningsJson());

        String ref = repositoryEntity.getDefaultBranch();
        List<GitHubTreeFile> treeFiles = List.of();
        List<GitHubConfigFile> configFiles = List.of();
        boolean treeTruncated = false;

        Optional<GitHubRepositoryTreeCache> treeCache = gitHubRepositoryTreeCacheRepository
                .findByRepositoryRepoIdAndRef(repositoryEntity.getRepoId(), ref);
        if (treeCache.isPresent()) {
            GitHubTreeApiResponse tree = deserializeTree(treeCache.get().getTreeJson());
            treeFiles = parseTreeFiles(tree);
            configFiles = parseConfigFiles(treeFiles);
            treeTruncated = tree != null && tree.truncated();
        }

        GitHubReadmeInfo readme = null;
        if (parseCache.getReadmePath() != null) {
            readme = new GitHubReadmeInfo(
                    parseCache.getReadmePath(),
                    parseCache.getReadmeContent(),
                    parseCache.getReadmeEncoding()
            );
        }

        return new ParsedRepositoryResponse(
                repositoryEntity.getRepoUuid(),
                owner,
                repo,
                owner + "/" + repo,
                repositoryEntity.getHtmlUrl(),
                repositoryEntity.getDescription(),
                ref,
                parseCache.getPrimaryLanguage(),
                parseCache.getStars(),
                parseCache.getForks(),
                parseCache.getOpenIssueCount(),
                parseCache.getOpenPullRequestCount(),
                parseCache.getOpenIssueAndPullRequestCount(),
                languages,
                treeTruncated,
                treeFiles,
                readme,
                configFiles,
                warnings,
                parseCache.getParsedAt()
        );
    }

    private String serializeLanguages(Map<String, Long> languages) {
        if (languages == null || languages.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(languages);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize languages for parse cache", e);
            return "{}";
        }
    }

    private Map<String, Long> deserializeLanguages(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, Long.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize languages from parse cache", e);
            return Map.of();
        }
    }

    private String serializeWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize warnings for parse cache", e);
            return "[]";
        }
    }

    private List<String> deserializeWarnings(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize warnings from parse cache", e);
            return List.of();
        }
    }

    private void saveParseCache(GitHubRepositoryEntity repositoryEntity,
                                GitHubRepositoryApiResponse repository,
                                Map<String, Long> languages,
                                long openIssueCount,
                                long openPullRequestCount,
                                long openIssueAndPullRequestCount,
                                GitHubReadmeInfo readme,
                                List<String> warnings) {
        String languagesJson = serializeLanguages(languages);
        String warningsJson = serializeWarnings(warnings);

        String readmePath = readme != null ? readme.path() : null;
        String readmeContent = readme != null ? readme.content() : null;
        String readmeEncoding = readme != null ? readme.encoding() : null;

        Optional<GitHubRepositoryParseCache> existing = gitHubRepositoryParseCacheRepository
                .findByRepositoryRepoId(repositoryEntity.getRepoId());

        if (existing.isPresent()) {
            existing.get().update(
                    repository.language(),
                    repository.stargazersCount(),
                    repository.forksCount(),
                    openIssueCount,
                    openPullRequestCount,
                    openIssueAndPullRequestCount,
                    languagesJson,
                    readmePath,
                    readmeContent,
                    readmeEncoding,
                    warningsJson
            );
        } else {
            gitHubRepositoryParseCacheRepository.save(new GitHubRepositoryParseCache(
                    repositoryEntity,
                    repository.language(),
                    repository.stargazersCount(),
                    repository.forksCount(),
                    openIssueCount,
                    openPullRequestCount,
                    openIssueAndPullRequestCount,
                    languagesJson,
                    readmePath,
                    readmeContent,
                    readmeEncoding,
                    warningsJson
            ));
        }
    }
}
