package com.ssafy.githubble.domain.auth.service;

import com.ssafy.githubble.domain.auth.client.GithubOAuthClient;
import com.ssafy.githubble.domain.auth.dto.enums.GithubLoginPurpose;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.exception.BusinessException;
import com.ssafy.githubble.global.properties.GithubOAuthProperties;
import com.ssafy.githubble.global.properties.JwtProperties;
import com.ssafy.githubble.domain.auth.domain.User;
import com.ssafy.githubble.domain.auth.dto.GithubUserResponse;
import com.ssafy.githubble.domain.auth.dto.TokenCookieResponse;
import com.ssafy.githubble.domain.auth.jwt.JwtTokenProvider;
import com.ssafy.githubble.domain.auth.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final GithubOAuthClient githubOAuthClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final GithubOAuthProperties githubOAuthProperties;
    private final JwtProperties jwtProperties;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HashMap<String, GithubLoginPurpose> stateMap = new HashMap<>();

    public String generateStateNonce() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public GithubLoginPurpose verifyNonce(String nonce){
        return stateMap.get(nonce);
    }

    public String createCookieString(String cookieName, String token, long maxAge){
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite(jwtProperties.getCookieSameSite())
                .path("/")
                .maxAge(Duration.ofMillis(maxAge))
                .build();
        return cookie.toString();
    }

    protected User saveUser(String code){
        String githubAccessToken = githubOAuthClient.exchangeCodeForAccessToken(code);
        GithubUserResponse githubUser = githubOAuthClient.fetchUser(githubAccessToken);
        String email = githubUser.email() == null
                ? githubOAuthClient.fetchPrimaryVerifiedEmail(githubAccessToken).orElse(null)
                : githubUser.email();

        User user = userRepository.findByGithubId(githubUser.id())
                .map(existingUser -> {
                    existingUser.updateGithubProfile(githubUser.login(), email, githubAccessToken, githubUser.avatarUrl());
                    return existingUser;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .githubId(githubUser.id())
                        .username(githubUser.login())
                        .email(email)
                        .githubAccessToken(githubAccessToken)
                        .avatarUrl(githubUser.avatarUrl())
                        .build()));
        return user;
    }

    public URI createGithubLoginUri(GithubLoginPurpose purpose) {
        String nonce = generateStateNonce();
        stateMap.put(nonce, purpose);
        return UriComponentsBuilder.fromUriString(githubOAuthProperties.getAuthorizeUrl())
                .queryParam("client_id", githubOAuthProperties.getClientId())
                .queryParam("redirect_uri", githubOAuthProperties.getRedirectUri())
                .queryParam("scope", githubOAuthProperties.getScope())
                .queryParam("state", purpose.toString() +":" + nonce)
                .build()
                .toUri();
    }

    @Transactional
    public TokenCookieResponse loginWithGithub(String code) {
        User user = saveUser(code);

        // 토큰 생성
        String accessToken = jwtTokenProvider.createToken(
                user.getAppUserId().toString(),
                jwtProperties.getAccessTokenExpirationMillis());
        String refreshToken = jwtTokenProvider.createToken(
                user.getAppUserId().toString(),
                jwtProperties.getRefreshTokenExpirationMillis());

        // 토큰을 쿠키로 가공하여 반환
        return new TokenCookieResponse(
                createCookieString("accessToken", accessToken, jwtProperties.getAccessTokenExpirationMillis()),
                createCookieString("refreshToken", refreshToken, jwtProperties.getRefreshTokenExpirationMillis())
        );
    }

    public String refreshAccessToken(Cookie[] cookies) {
        if (cookies == null)
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요.");

        String refreshToken = Arrays.stream(cookies)
                .filter(cookie -> "refreshToken".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (!jwtTokenProvider.validateToken(refreshToken))
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요.");

        String userId = jwtTokenProvider.getContent(refreshToken);
        String accessToken = jwtTokenProvider.createToken(userId, jwtProperties.getAccessTokenExpirationMillis());
        return createCookieString("accessToken", accessToken, jwtProperties.getAccessTokenExpirationMillis());
    }

    public Long getUserIdFromAccessToken(Cookie[] cookies) {
        if (cookies == null)
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요.");

        String accessToken = Arrays.stream(cookies)
                .filter(cookie -> "accessToken".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (!jwtTokenProvider.validateToken(accessToken))
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요.");

        return Long.parseLong(jwtTokenProvider.getContent(accessToken));
    }

    @Transactional
    public void withdrawal(Long userId, String code){
        String githubAccessTokenFromApi = githubOAuthClient.exchangeCodeForAccessToken(code);
        GithubUserResponse githubUser = githubOAuthClient.fetchUser(githubAccessTokenFromApi);
        Long githubIdFromApi = githubUser.id();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 사용자입니다."));
        if(!user.getGithubId().equals(githubIdFromApi)) throw new BusinessException(ErrorCode.FAIL, "사용자 정보가 일치하지 않습니다.");

        githubOAuthClient.revokeAuthorization(githubAccessTokenFromApi);
        user.softDelete();
    }
}
