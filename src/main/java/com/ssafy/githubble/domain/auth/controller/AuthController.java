package com.ssafy.githubble.domain.auth.controller;

import com.ssafy.githubble.domain.auth.dto.TokenCookieResponse;
import com.ssafy.githubble.domain.auth.dto.enums.GithubLoginPurpose;
import com.ssafy.githubble.domain.auth.service.AuthService;
import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.dto.ApiResult;
import com.ssafy.githubble.global.exception.BusinessException;
import com.ssafy.githubble.global.properties.GlobalProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "GitHub OAuth 로그인, 토큰 재발급, 로그아웃, 회원 탈퇴 API")
public class AuthController {
    private final GlobalProperties globalProperties;

    private final AuthService authService;

    @Operation(
            summary = "GitHub OAuth 로그인 시작",
            description = "LOGIN 목적 state를 생성한 뒤 GitHub 인증 페이지로 리다이렉트합니다. Swagger Execute보다 브라우저 주소창에서 직접 호출하는 방식이 OAuth 테스트에 적합합니다."
    )
    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authService.createGithubLoginUri(GithubLoginPurpose.LOGIN))
                .build();
    }

    @Operation(
            summary = "GitHub OAuth 콜백 처리",
            description = "GitHub 인가 code와 LOGIN/WITHDRAWAL 목적이 포함된 state를 검증합니다. LOGIN이면 accessToken/refreshToken HttpOnly Cookie를 발급하고, WITHDRAWAL이면 GitHub authorization revoke 후 회원을 탈퇴 처리합니다."
    )
    @GetMapping("/callback")
    public ResponseEntity<?> callback(HttpServletRequest request,
                                         @Parameter(description = "GitHub OAuth 인가 코드", example = "b7d9f2c0e8a4f6...")
                                         @RequestParam String code,
                                         @Parameter(description = "백엔드가 로그인/탈퇴 목적과 nonce를 담아 생성한 OAuth state", example = "LOGIN:Qp8xY2NwR5mK...")
                                         @RequestParam String state) {
        String[] stateInfo = state.split(":");
        if(stateInfo.length < 2) throw new BusinessException(ErrorCode.FAIL);
        GithubLoginPurpose purpose = GithubLoginPurpose.valueOf(stateInfo[0]);
        String nonce = stateInfo[1];

        if(authService.verifyNonce(nonce) != purpose) throw new BusinessException(ErrorCode.FAIL);

        if(purpose == GithubLoginPurpose.LOGIN){
            TokenCookieResponse tokenCookieResponse = authService.loginWithGithub(code);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, tokenCookieResponse.accessToken())
                    .header(HttpHeaders.SET_COOKIE, tokenCookieResponse.refreshToken())
                    .location(URI.create(
                            globalProperties.getFeBaseUrl() + globalProperties.getFeLoginSuccessUri()
                    ))
                    .build();
        }
        // 탈퇴인 경우
        Long userId = authService.getUserIdFromAccessToken(request.getCookies());
        authService.withdrawal(userId, code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, authService.createCookieString("accessToken", "", 0))
                .header(HttpHeaders.SET_COOKIE, authService.createCookieString("refreshToken", "", 0))
                .location(URI.create(
                        globalProperties.getFeBaseUrl()
                ))
                .build();
    }

    @Operation(
            summary = "Access Token 재발급",
            description = "refreshToken HttpOnly Cookie를 검증해 새 accessToken Cookie를 Set-Cookie 헤더로 발급합니다."
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResult<Void>> refresh(HttpServletRequest request) {
        String accessTokenCookie = authService.refreshAccessToken(
                request.getCookies()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, accessTokenCookie);

        return ApiResult.success(headers);
    }

    @Operation(
            summary = "로그아웃",
            description = "현재 accessToken 인증을 통과한 사용자의 accessToken/refreshToken Cookie를 만료시킵니다. AuthFilter 보호 대상입니다.",
            security = @SecurityRequirement(name = "cookieAuth")
    )
    @DeleteMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, authService.createCookieString("accessToken", "", 0));
        headers.add(HttpHeaders.SET_COOKIE, authService.createCookieString("refreshToken", "", 0));
        return ApiResult.success(headers);
    }

    @Operation(
            summary = "GitHub OAuth 회원 탈퇴 시작",
            description = "WITHDRAWAL 목적 state를 생성한 뒤 GitHub 인증 페이지로 리다이렉트합니다. callback에서 GitHub 사용자 일치 여부 확인, authorization revoke, soft delete를 수행합니다. AuthFilter 보호 대상입니다.",
            security = @SecurityRequirement(name = "cookieAuth")
    )
    @GetMapping("/withdrawal")
    public ResponseEntity<Void> withdrawal() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authService.createGithubLoginUri(GithubLoginPurpose.WITHDRAWAL))
                .build();
    }
}
