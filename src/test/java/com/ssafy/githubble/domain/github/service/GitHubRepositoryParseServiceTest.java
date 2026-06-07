package com.ssafy.githubble.domain.github.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.githubble.domain.github.client.GitHubApiClient;
import com.ssafy.githubble.domain.github.dto.external.GitHubContentApiResponse;
import com.ssafy.githubble.domain.github.dto.external.GitHubRepositoryApiResponse;
import com.ssafy.githubble.domain.github.dto.external.GitHubTreeApiResponse;
import com.ssafy.githubble.domain.github.dto.response.GitHubFileContentResponse;
import com.ssafy.githubble.domain.github.dto.response.GitHubFileSearchResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubRepositoryParseServiceTest {

    private static final Long APPUSER_ID = 1L;
    private static final String GITHUB_ACCESS_TOKEN = "test-github-access-token";

    private GitHubApiClient gitHubApiClient;
    private GithubAccessTokenProvider githubAccessTokenProvider;
    private GitHubRepositoryRepository gitHubRepositoryRepository;
    private GitHubFileContentCacheRepository gitHubFileContentCacheRepository;
    private GitHubRepositoryTreeCacheRepository gitHubRepositoryTreeCacheRepository;
    private GitHubRepositoryParseCacheRepository gitHubRepositoryParseCacheRepository;
    private GitHubRepositoryParseService service;

    @BeforeEach
    void setUp() {
        gitHubApiClient = mock(GitHubApiClient.class);
        githubAccessTokenProvider = mock(GithubAccessTokenProvider.class);
        gitHubRepositoryRepository = mock(GitHubRepositoryRepository.class);
        gitHubFileContentCacheRepository = mock(GitHubFileContentCacheRepository.class);
        gitHubRepositoryTreeCacheRepository = mock(GitHubRepositoryTreeCacheRepository.class);
        gitHubRepositoryParseCacheRepository = mock(GitHubRepositoryParseCacheRepository.class);
        service = new GitHubRepositoryParseService(
                gitHubApiClient,
                githubAccessTokenProvider,
                gitHubRepositoryRepository,
                gitHubFileContentCacheRepository,
                gitHubRepositoryTreeCacheRepository,
                gitHubRepositoryParseCacheRepository,
                new ObjectMapper()
        );
        when(gitHubRepositoryRepository.save(ArgumentMatchers.any(GitHubRepositoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(gitHubFileContentCacheRepository.save(ArgumentMatchers.any(GitHubFileContentCache.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(gitHubRepositoryTreeCacheRepository.save(ArgumentMatchers.any(GitHubRepositoryTreeCache.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(gitHubRepositoryParseCacheRepository.save(ArgumentMatchers.any(GitHubRepositoryParseCache.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("로그인 사용자의 GitHub 토큰으로 저장소를 파싱한다")
    void parseRepositoryWithLoginUserGithubAccessToken() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "spring-projects", "spring-boot"))
                .thenReturn(repository());
        when(gitHubApiClient.getLanguages(GITHUB_ACCESS_TOKEN, "spring-projects", "spring-boot"))
                .thenReturn(Map.of("Java", 1000L, "Kotlin", 100L));
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "spring-projects", "spring-boot", "main"))
                .thenReturn(tree(false));
        when(gitHubApiClient.getReadme(GITHUB_ACCESS_TOKEN, "spring-projects", "spring-boot"))
                .thenReturn(Optional.of(readme("# Spring Boot")));

        final ParsedRepositoryResponse response = service.parse(APPUSER_ID, "spring-projects", "spring-boot");

        assertThat(response.fullName()).isEqualTo("spring-projects/spring-boot");
        assertThat(response.defaultBranch()).isEqualTo("main");
        assertThat(response.primaryLanguage()).isEqualTo("Java");
        assertThat(response.languages()).containsEntry("Java", 1000L);
        assertThat(response.readme().content()).isEqualTo("# Spring Boot");
        assertThat(response.treeFiles()).extracting("path")
                .contains("build.gradle", "Dockerfile", ".github/workflows/ci.yml", "src/main/java/App.java");
        assertThat(response.treeFiles()).extracting("sha")
                .contains("sha1", "sha2", "sha3", "sha4");
        assertThat(response.configFiles()).extracting("path")
                .contains("build.gradle", "Dockerfile", ".github/workflows/ci.yml");
        assertThat(response.warnings()).isEmpty();

        verify(gitHubApiClient).getRepository(GITHUB_ACCESS_TOKEN, "spring-projects", "spring-boot");
        verify(gitHubApiClient).getLanguages(GITHUB_ACCESS_TOKEN, "spring-projects", "spring-boot");
        verify(gitHubApiClient).getTree(GITHUB_ACCESS_TOKEN, "spring-projects", "spring-boot", "main");
        verify(gitHubApiClient).getReadme(GITHUB_ACCESS_TOKEN, "spring-projects", "spring-boot");
    }

    @Test
    @DisplayName("저장소 파싱 주요 stage를 github.parse.event 형태로 로깅한다")
    void parseRepositoryEmitsStructuredStageLogs() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getLanguages(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(Map.of("Java", 1000L));
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main")).thenReturn(tree(false));
        when(gitHubApiClient.getReadme(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(Optional.empty());
        ListAppender<ILoggingEvent> appender = attachParseServiceListAppender();

        service.parse(APPUSER_ID, "owner", "repo");

        List<String> messages = parseEvents(appender).stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("github.parse.event")
                .contains("event=load_token")
                .contains("owner=owner")
                .contains("repo=repo"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("event=repository_metadata"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("event=language_statistics"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("event=issue_pr_count"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("event=tree_load").contains("ref=main"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("event=readme_load"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("event=config_extract"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("event=parse_completed"));
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(GITHUB_ACCESS_TOKEN));
    }

    @Test
    @DisplayName("README가 없는 저장소는 경고를 포함해 파싱한다")
    void parseRepositoryWithoutReadme() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getLanguages(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(Map.of());
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main")).thenReturn(tree(false));
        when(gitHubApiClient.getReadme(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(Optional.empty());

        final ParsedRepositoryResponse response = service.parse(APPUSER_ID, "owner", "repo");

        assertThat(response.readme()).isNull();
        assertThat(response.warnings()).contains("README 파일을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("GitHub 파일 트리가 잘린 경우 treeTruncated를 true로 반환한다")
    void parseRepositoryWithTruncatedTree() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getLanguages(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(Map.of());
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main")).thenReturn(tree(true));
        when(gitHubApiClient.getReadme(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(Optional.empty());

        final ParsedRepositoryResponse response = service.parse(APPUSER_ID, "owner", "repo");

        assertThat(response.treeTruncated()).isTrue();
    }

    @Test
    @DisplayName("README 내용 디코딩에 실패하면 경고와 null content를 반환한다")
    void parseRepositoryWithInvalidReadmeContent() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getLanguages(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(Map.of());
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main")).thenReturn(tree(false));
        when(gitHubApiClient.getReadme(GITHUB_ACCESS_TOKEN, "owner", "repo"))
                .thenReturn(Optional.of(new GitHubContentApiResponse(
                        "README.md",
                        "README.md",
                        "sha",
                        10L,
                        "file",
                        "base64",
                        "not-base64",
                        "https://github.com/owner/repo/blob/main/README.md"
                )));

        final ParsedRepositoryResponse response = service.parse(APPUSER_ID, "owner", "repo");

        assertThat(response.readme().content()).isNull();
        assertThat(response.warnings()).contains("README 파일을 디코딩하지 못했습니다.");
    }

    @Test
    @DisplayName("owner 값이 비어 있으면 입력 오류가 발생한다")
    void parseRepositoryWithBlankOwner() {
        assertThatThrownBy(() -> service.parse(APPUSER_ID, " ", "repo"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("인증 사용자 ID가 없으면 인증 오류가 발생한다")
    void parseRepositoryWithoutAuthenticatedUserId() {
        when(githubAccessTokenProvider.getGithubAccessToken(null))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        assertThatThrownBy(() -> service.parse(null, "owner", "repo"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("저장된 사용자가 없으면 인증 오류가 발생한다")
    void parseRepositoryWithoutSavedUser() {
        when(githubAccessTokenProvider.getGithubAccessToken(APPUSER_ID))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        assertThatThrownBy(() -> service.parse(APPUSER_ID, "owner", "repo"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GitHub access token이 없으면 인증 오류가 발생한다")
    void parseRepositoryWithoutGithubAccessToken() {
        when(githubAccessTokenProvider.getGithubAccessToken(APPUSER_ID))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "GitHub 재로그인이 필요합니다."));

        assertThatThrownBy(() -> service.parse(APPUSER_ID, "owner", "repo"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("base64 텍스트 파일 내용을 디코딩하고 캐시에 저장한다")
    void getFileContentDecodesBase64TextFileAndStoresCache() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getContent(GITHUB_ACCESS_TOKEN, "owner", "repo", "src/App.java", "main"))
                .thenReturn(content("src/App.java", "class App {}", "file-sha", 12L));
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(null, "main", "src/App.java"))
                .thenReturn(Optional.empty());

        GitHubFileContentResponse response = service.getFileContent(APPUSER_ID, "owner", "repo", "src/App.java", null);

        assertThat(response.content()).isEqualTo("class App {}");
        assertThat(response.contentAvailable()).isTrue();
        assertThat(response.cached()).isFalse();
        verify(gitHubFileContentCacheRepository).save(ArgumentMatchers.any(GitHubFileContentCache.class));
    }

    @Test
    @DisplayName("유효한 파일 캐시가 있으면 GitHub Contents API를 호출하지 않는다")
    void getFileContentUsesFreshCacheWithoutCallingGithubContentApi() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        GitHubFileContentCache freshCache = fileContentCache(savedRepository, "src/App.java", "class App {}", "file-sha", 12L);
        setField(freshCache, "fetchedAt", LocalDateTime.now().minusHours(1));
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getContent(GITHUB_ACCESS_TOKEN, "owner", "repo", "src/App.java", "main"))
                .thenReturn(content("src/App.java", "class App {}", "file-sha", 12L));
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(null, "main", "src/App.java"))
                .thenReturn(Optional.of(freshCache));

        final GitHubFileContentResponse response = service.getFileContent(APPUSER_ID, "owner", "repo", "src/App.java", null);

        assertThat(response.content()).isEqualTo("class App {}");
        assertThat(response.cached()).isTrue();
        verify(githubAccessTokenProvider, never()).getGithubAccessToken(APPUSER_ID);
        verify(gitHubApiClient, never()).getContent(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("유효한 FILE_TOO_LARGE 캐시가 있으면 GitHub Contents API를 호출하지 않는다")
    void getFileContentUsesFreshFileTooLargeCacheWithoutCallingGithubContentApi() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        GitHubFileContentCache freshCache = new GitHubFileContentCache(
                savedRepository,
                "main",
                "large.log",
                "large.log",
                "large-sha",
                "file",
                2L * 1024L * 1024L,
                "none",
                null,
                false,
                false,
                "FILE_TOO_LARGE",
                "html-url"
        );
        setField(freshCache, "fetchedAt", LocalDateTime.now().minusHours(1));
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(null, "main", "large.log"))
                .thenReturn(Optional.of(freshCache));

        final GitHubFileContentResponse response = service.getFileContent(APPUSER_ID, "owner", "repo", "large.log", null);

        assertThat(response.content()).isNull();
        assertThat(response.contentAvailable()).isFalse();
        assertThat(response.unavailableReason()).isEqualTo("FILE_TOO_LARGE");
        assertThat(response.cached()).isTrue();
        verify(githubAccessTokenProvider, never()).getGithubAccessToken(APPUSER_ID);
        verify(gitHubApiClient, never()).getRepository(anyString(), anyString(), anyString());
        verify(gitHubApiClient, never()).getContent(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("만료된 파일 캐시의 SHA가 변경되면 GitHub Contents API로 다시 조회한다")
    void getFileContentRefreshesExpiredCacheWhenShaChanged() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        GitHubFileContentCache expiredCache = fileContentCache(savedRepository, "src/main/java/App.java", "old content", "old-sha", 11L);
        setField(expiredCache, "fetchedAt", LocalDateTime.now().minusHours(25));
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main"))
                .thenReturn(tree(false));
        when(gitHubApiClient.getContent(GITHUB_ACCESS_TOKEN, "owner", "repo", "src/main/java/App.java", "main"))
                .thenReturn(content("src/main/java/App.java", "new content", "new-sha", 11L));
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(null, "main", "src/main/java/App.java"))
                .thenReturn(Optional.of(expiredCache));

        final GitHubFileContentResponse response = service.getFileContent(APPUSER_ID, "owner", "repo", "src/main/java/App.java", null);

        assertThat(response.content()).isEqualTo("new content");
        assertThat(response.cached()).isFalse();
        assertThat(expiredCache.getSha()).isEqualTo("new-sha");
        verify(gitHubApiClient).getContent(GITHUB_ACCESS_TOKEN, "owner", "repo", "src/main/java/App.java", "main");
    }

    @Test
    @DisplayName("만료된 파일 캐시의 SHA가 같으면 GitHub Contents API를 호출하지 않는다")
    void getFileContentUsesExpiredCacheWhenShaUnchanged() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        GitHubFileContentCache expiredCache = fileContentCache(savedRepository, "src/main/java/App.java", "class App {}", "sha4", 40L);
        setField(expiredCache, "fetchedAt", LocalDateTime.now().minusHours(25));
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main"))
                .thenReturn(tree(false));
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(null, "main", "src/main/java/App.java"))
                .thenReturn(Optional.of(expiredCache));

        final GitHubFileContentResponse response = service.getFileContent(APPUSER_ID, "owner", "repo", "src/main/java/App.java", null);

        assertThat(response.content()).isEqualTo("class App {}");
        assertThat(response.cached()).isTrue();
        verify(gitHubApiClient).getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main");
        verify(gitHubApiClient, never()).getContent(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("파일 내용 조회 시 디렉터리 경로는 전용 오류가 발생한다")
    void getFileContentRejectsDirectoryPath() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getContent(GITHUB_ACCESS_TOKEN, "owner", "repo", "src", "main"))
                .thenReturn(new GitHubContentApiResponse(
                        "src",
                        "src",
                        "dir-sha",
                        null,
                        "dir",
                        null,
                        null,
                        "https://github.com/owner/repo/tree/main/src"
                ));

        assertThatThrownBy(() -> service.getFileContent(APPUSER_ID, "owner", "repo", "src", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GITHUB_CONTENT_IS_DIRECTORY);
    }

    @Test
    @DisplayName("private 저장소 파일 내용 조회는 지원하지 않는다는 오류가 발생한다")
    void getFileContentRejectsPrivateRepository() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(privateRepository());

        assertThatThrownBy(() -> service.getFileContent(APPUSER_ID, "owner", "repo", "README.md", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GITHUB_PRIVATE_REPOSITORY_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("큰 파일은 contentAvailable false와 사유를 반환한다")
    void getFileContentReturnsUnavailableForLargeFile() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getContent(GITHUB_ACCESS_TOKEN, "owner", "repo", "large.log", "main"))
                .thenReturn(new GitHubContentApiResponse(
                        "large.log",
                        "large.log",
                        "large-sha",
                        2L * 1024L * 1024L,
                        "file",
                        "none",
                        "",
                        "html-url"
                ));
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(null, "main", "large.log"))
                .thenReturn(Optional.empty());

        GitHubFileContentResponse response = service.getFileContent(APPUSER_ID, "owner", "repo", "large.log", null);

        assertThat(response.contentAvailable()).isFalse();
        assertThat(response.unavailableReason()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("저장소 캐시 총량이 1MB를 넘으면 오래된 파일 캐시를 제거한다")
    void getFileContentEvictsOldCachesWhenRepositoryCacheLimitExceeded() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        GitHubFileContentCache oldCache = fileContentCache(savedRepository, "old/App.java", "old content", "old-sha", 400L * 1024L);
        setField(oldCache, "fetchedAt", LocalDateTime.now().minusHours(1));
        setField(oldCache, "lastAccessedAt", LocalDateTime.now().minusHours(2));
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getContent(GITHUB_ACCESS_TOKEN, "owner", "repo", "src/Large.java", "main"))
                .thenReturn(content("src/Large.java", "x".repeat(700 * 1024), "large-cache-sha", 700L * 1024L));
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(null, "main", "src/Large.java"))
                .thenReturn(Optional.empty());
        when(gitHubFileContentCacheRepository.sumCachedContentSize(null)).thenReturn(900L * 1024L);
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndContentAvailableTrueOrderByLastAccessedAtAsc(null))
                .thenReturn(List.of(oldCache));

        service.getFileContent(APPUSER_ID, "owner", "repo", "src/Large.java", null);

        verify(gitHubFileContentCacheRepository).delete(oldCache);
    }

    @Test
    @DisplayName("유효한 트리 캐시가 있으면 GitHub Tree API를 호출하지 않는다")
    void searchFilesUsesFreshTreeCacheWithoutCallingGithubTreeApi() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        GitHubRepositoryTreeCache treeCache = treeCache(savedRepository, treeWithDuplicateFileNamesJson());
        setField(treeCache, "fetchedAt", LocalDateTime.now().minusHours(1));
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubRepositoryTreeCacheRepository.findByRepositoryRepoIdAndRef(null, "main"))
                .thenReturn(Optional.of(treeCache));

        GitHubFileSearchResponse response = service.searchFiles(APPUSER_ID, "owner", "repo", "Dockerfile", null);

        assertThat(response.matches()).hasSize(2);
        verify(gitHubApiClient, never()).getTree(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("만료된 트리 캐시는 GitHub Tree API로 다시 조회한다")
    void searchFilesRefreshesExpiredTreeCache() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        GitHubRepositoryTreeCache treeCache = treeCache(savedRepository, treeWithDuplicateFileNamesJson());
        setField(treeCache, "fetchedAt", LocalDateTime.now().minusHours(25));
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubRepositoryTreeCacheRepository.findByRepositoryRepoIdAndRef(null, "main"))
                .thenReturn(Optional.of(treeCache));
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main"))
                .thenReturn(treeWithDuplicateFileNames());

        GitHubFileSearchResponse response = service.searchFiles(APPUSER_ID, "owner", "repo", "Dockerfile", null);

        assertThat(response.matches()).hasSize(2);
        assertThat(treeCache.getTreeSha()).isEqualTo("sha");
        verify(gitHubApiClient).getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main");
    }

    @Test
    @DisplayName("파일명 검색 결과가 여러 개이면 후보 목록만 반환한다")
    void searchFilesByNameReturnsMultipleMatchesWithoutContent() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main"))
                .thenReturn(treeWithDuplicateFileNames());

        GitHubFileSearchResponse response = service.searchFiles(APPUSER_ID, "owner", "repo", "Dockerfile", null);

        assertThat(response.matches()).hasSize(2);
        assertThat(response.singleMatchContent()).isNull();
    }

    @Test
    @DisplayName("경로 검색 결과가 하나이면 파일 내용까지 반환한다")
    void searchFilesByPathReturnsSingleMatchWithContent() {
        givenGithubAccessToken(GITHUB_ACCESS_TOKEN);
        when(gitHubApiClient.getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo")).thenReturn(repository());
        when(gitHubApiClient.getTree(GITHUB_ACCESS_TOKEN, "owner", "repo", "main"))
                .thenReturn(treeWithDuplicateFileNames());
        when(gitHubApiClient.getContent(GITHUB_ACCESS_TOKEN, "owner", "repo", "be/Dockerfile", "main"))
                .thenReturn(content("be/Dockerfile", "FROM eclipse-temurin:21", "docker-sha", 22L));
        when(gitHubFileContentCacheRepository.findByRepositoryRepoIdAndRefAndPath(null, "main", "be/Dockerfile"))
                .thenReturn(Optional.empty());

        GitHubFileSearchResponse response = service.searchFiles(APPUSER_ID, "owner", "repo", "be/Dockerfile", null);

        assertThat(response.matches()).hasSize(1);
        assertThat(response.singleMatchContent().content()).isEqualTo("FROM eclipse-temurin:21");
        verify(gitHubApiClient).getRepository(GITHUB_ACCESS_TOKEN, "owner", "repo");
    }

    @Test
    @DisplayName("파싱 캐시가 존재하면 hasFreshParseCache는 true를 반환한다")
    void hasFreshParseCacheReturnsTrueWhenCacheExists() {
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        setField(savedRepository, "repoId", 1L);
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubRepositoryParseCacheRepository.findByRepositoryRepoId(1L))
                .thenReturn(Optional.of(mock(GitHubRepositoryParseCache.class)));

        boolean result = service.hasFreshParseCache("owner", "repo");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("파싱 캐시가 없으면 hasFreshParseCache는 false를 반환한다")
    void hasFreshParseCacheReturnsFalseWhenCacheAbsent() {
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        setField(savedRepository, "repoId", 1L);
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubRepositoryParseCacheRepository.findByRepositoryRepoId(1L))
                .thenReturn(Optional.empty());

        boolean result = service.hasFreshParseCache("owner", "repo");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("저장소가 DB에 없으면 hasFreshParseCache는 false를 반환한다")
    void hasFreshParseCacheReturnsFalseWhenRepositoryNotInDb() {
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.empty());

        boolean result = service.hasFreshParseCache("owner", "repo");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("파싱 캐시가 있으면 DB에서 재구성한 ParsedRepositoryResponse를 반환한다")
    void getParsedFromCacheReturnsParsedRepositoryResponse() {
        GitHubRepositoryEntity savedRepository = repositoryEntity();
        setField(savedRepository, "repoId", 1L);
        GitHubRepositoryParseCache parseCache = new GitHubRepositoryParseCache(
                savedRepository,
                "Java",
                10L,
                2L,
                7L,
                3L,
                10L,
                "{\"Java\":1000,\"Kotlin\":100}",
                "README.md",
                "# Spring Boot",
                "base64",
                "[]"
        );
        GitHubRepositoryTreeCache treeCache = treeCache(savedRepository, treeJsonForCache());
        when(gitHubRepositoryRepository.findByOwnerLoginIgnoreCaseAndRepoNameIgnoreCase("owner", "repo"))
                .thenReturn(Optional.of(savedRepository));
        when(gitHubRepositoryParseCacheRepository.findByRepositoryRepoId(1L))
                .thenReturn(Optional.of(parseCache));
        when(gitHubRepositoryTreeCacheRepository.findByRepositoryRepoIdAndRef(1L, "main"))
                .thenReturn(Optional.of(treeCache));

        ParsedRepositoryResponse response = service.getParsedFromCache("owner", "repo");

        assertThat(response.owner()).isEqualTo("owner");
        assertThat(response.repo()).isEqualTo("repo");
        assertThat(response.fullName()).isEqualTo("owner/repo");
        assertThat(response.primaryLanguage()).isEqualTo("Java");
        assertThat(response.stars()).isEqualTo(10L);
        assertThat(response.forks()).isEqualTo(2L);
        assertThat(response.openIssueCount()).isEqualTo(7L);
        assertThat(response.openPullRequestCount()).isEqualTo(3L);
        assertThat(response.openIssueAndPullRequestCount()).isEqualTo(10L);
        assertThat(response.languages()).containsEntry("Java", 1000L);
        assertThat(response.treeFiles()).extracting("path")
                .contains("build.gradle", "src/main/java/App.java");
        assertThat(response.configFiles()).extracting("path")
                .contains("build.gradle", "Dockerfile");
        assertThat(response.readme().content()).isEqualTo("# Spring Boot");
        assertThat(response.warnings()).isEmpty();
    }

    private ListAppender<ILoggingEvent> attachParseServiceListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(GitHubRepositoryParseService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private List<ILoggingEvent> parseEvents(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("github.parse.event"))
                .toList();
    }

    private void givenGithubAccessToken(String githubAccessToken) {
        when(githubAccessTokenProvider.getGithubAccessToken(APPUSER_ID))
                .thenReturn(githubAccessToken);
    }

    private GitHubRepositoryEntity repositoryEntity() {
        return new GitHubRepositoryEntity(
                100L,
                "owner",
                "repo",
                "main",
                "Repository",
                false,
                "https://github.com/owner/repo"
        );
    }

    private GitHubFileContentCache fileContentCache(GitHubRepositoryEntity repository, String path, String content,
                                                    String sha, Long size) {
        return new GitHubFileContentCache(
                repository,
                "main",
                path,
                path.substring(path.lastIndexOf('/') + 1),
                sha,
                "file",
                size,
                "base64",
                content,
                true,
                false,
                null,
                "html-url"
        );
    }

    private GitHubRepositoryTreeCache treeCache(GitHubRepositoryEntity repository, String treeJson) {
        return new GitHubRepositoryTreeCache(repository, "main", "old-sha", false, treeJson);
    }

    private String treeWithDuplicateFileNamesJson() {
        return """
                {
                  "sha": "sha",
                  "url": "url",
                  "tree": [
                    {"path": "Dockerfile", "mode": "100644", "type": "blob", "sha": "sha1", "size": 100, "url": "url1"},
                    {"path": "be/Dockerfile", "mode": "100644", "type": "blob", "sha": "sha2", "size": 120, "url": "url2"},
                    {"path": "be", "mode": "040000", "type": "tree", "sha": "sha3", "url": "url3"}
                  ],
                  "truncated": false
                }
                """;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private GitHubRepositoryApiResponse repository() {
        return new GitHubRepositoryApiResponse(
                100L,
                "spring-boot",
                "spring-projects/spring-boot",
                "https://github.com/spring-projects/spring-boot",
                "Spring Boot",
                "main",
                "Java",
                false,
                10L,
                2L,
                3L
        );
    }

    private GitHubTreeApiResponse tree(boolean truncated) {
        return new GitHubTreeApiResponse(
                "sha",
                "url",
                List.of(
                        new GitHubTreeApiResponse.TreeItem("build.gradle", "100644", "blob", "sha1", 100L, "url"),
                        new GitHubTreeApiResponse.TreeItem("Dockerfile", "100644", "blob", "sha2", 20L, "url"),
                        new GitHubTreeApiResponse.TreeItem(".github/workflows/ci.yml", "100644", "blob", "sha3", 30L, "url"),
                        new GitHubTreeApiResponse.TreeItem("src/main/java/App.java", "100644", "blob", "sha4", 40L, "url"),
                        new GitHubTreeApiResponse.TreeItem("src/main/java", "040000", "tree", "sha5", null, "url")
                ),
                truncated
        );
    }

    private GitHubRepositoryApiResponse privateRepository() {
        return new GitHubRepositoryApiResponse(
                200L,
                "repo",
                "owner/repo",
                "https://github.com/owner/repo",
                "Private Repo",
                "main",
                "Java",
                true,
                1L,
                1L,
                1L
        );
    }

    private GitHubTreeApiResponse treeWithDuplicateFileNames() {
        return new GitHubTreeApiResponse(
                "sha",
                "url",
                List.of(
                        new GitHubTreeApiResponse.TreeItem("Dockerfile", "100644", "blob", "sha1", 100L, "url1"),
                        new GitHubTreeApiResponse.TreeItem("be/Dockerfile", "100644", "blob", "sha2", 120L, "url2"),
                        new GitHubTreeApiResponse.TreeItem("be", "040000", "tree", "sha3", null, "url3")
                ),
                false
        );
    }

    private static GitHubContentApiResponse content(String path, String content, String sha, Long size) {
        final String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        final String name = path.substring(path.lastIndexOf('/') + 1);
        return new GitHubContentApiResponse(
                name,
                path,
                sha,
                size,
                "file",
                "base64",
                encoded,
                "html-url"
        );
    }

    private static GitHubContentApiResponse readme(String content) {
        final String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        return new GitHubContentApiResponse(
                "README.md",
                "README.md",
                "sha",
                10L,
                "file",
                "base64",
                encoded,
                "https://github.com/spring-projects/spring-boot/blob/main/README.md"
        );
    }

    private static String treeJsonForCache() {
        return """
                {
                  "sha": "sha",
                  "url": "url",
                  "tree": [
                    {"path": "build.gradle", "mode": "100644", "type": "blob", "sha": "sha1", "size": 100, "url": "url1"},
                    {"path": "Dockerfile", "mode": "100644", "type": "blob", "sha": "sha2", "size": 20, "url": "url2"},
                    {"path": "src/main/java/App.java", "mode": "100644", "type": "blob", "sha": "sha4", "size": 40, "url": "url4"}
                  ],
                  "truncated": false
                }
                """;
    }
}
