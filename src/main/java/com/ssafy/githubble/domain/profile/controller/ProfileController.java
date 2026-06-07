package com.ssafy.githubble.domain.profile.controller;

import com.ssafy.githubble.domain.profile.dto.ContributionCalendarResponse;
import com.ssafy.githubble.domain.profile.dto.ProfileResponse;
import com.ssafy.githubble.domain.profile.dto.TechStackResponse;
import com.ssafy.githubble.domain.profile.dto.UpdateMyTechStacksRequest;
import com.ssafy.githubble.domain.profile.service.ProfileContributionService;
import com.ssafy.githubble.domain.profile.dto.RepoPageResponse;
import com.ssafy.githubble.domain.profile.dto.RepoResponse;
import com.ssafy.githubble.domain.profile.service.MyGithubRepositoryService;
import com.ssafy.githubble.domain.profile.service.PopularRepositoryService;
import com.ssafy.githubble.domain.profile.service.ProfileService;
import com.ssafy.githubble.domain.profile.service.TechStackService;
import com.ssafy.githubble.global.code.SuccessCode;
import com.ssafy.githubble.global.dto.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/profiles")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "GitHub 사용자 프로필 조회 API")
@SecurityRequirement(name = "cookieAuth")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileContributionService profileContributionService;
    private final MyGithubRepositoryService myGithubRepositoryService;
    private final PopularRepositoryService popularRepositoryService;
    private final TechStackService techStackService;

    @Operation(
            summary = "GitHub 나의 프로필 조회",
            description = "username에 해당하는 GitHub 사용자 프로필을 GitHub API에서 조회하고, 프로필 정보를 저장 또는 갱신한 뒤 반환합니다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResult<ProfileResponse>> getMyProfile(
            HttpServletRequest request
    ) {
        Long appuserId = (Long) request.getAttribute("appuserId");
        ProfileResponse response = profileService.getMyProfile(appuserId);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "GitHub 사용자 프로필 조회",
            description = "username에 해당하는 GitHub 사용자 프로필을 GitHub API에서 조회하고, 프로필 정보를 저장 또는 갱신한 뒤 반환합니다."
    )
    @GetMapping("/{username}")
    public ResponseEntity<ApiResult<ProfileResponse>> getProfile(
            HttpServletRequest request,
            @Parameter(description = "조회할 GitHub 사용자명", example = "octocat")
            @PathVariable String username
    ) {
        Long appuserId = (Long) request.getAttribute("appuserId");
        ProfileResponse response = profileService.getProfile(appuserId, username);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "나의 GitHub 레포 목록 조회",
            description = "GitHub 동기화 시점을 기준으로 변경분을 반영한 뒤, 나의 레포 목록을 반환합니다."
    )
    @GetMapping("/me/repositories")
    public ResponseEntity<ApiResult<RepoPageResponse>> getMyRepositories(
            HttpServletRequest request,
            @ParameterObject
            @PageableDefault(size = 6, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long appuserId = (Long) request.getAttribute("appuserId");

        Page<RepoResponse> result = myGithubRepositoryService.getMyRepositoryList(appuserId, pageable);

        RepoPageResponse response = new RepoPageResponse(
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.getContent()
        );

        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "나의 대표(최다 스타) 레포 조회",
            description = "GitHub Search API에서 스타 수 내림차순 기준으로 나의 레포 1개를 조회합니다."
    )
    @GetMapping("/me/repositories/most-popular")
    public ResponseEntity<ApiResult<RepoResponse>> getMyMostPopularRepository(
            HttpServletRequest request
    ) {
        Long appuserId = (Long) request.getAttribute("appuserId");
        RepoResponse response = popularRepositoryService.getMyMostPopularRepository(appuserId);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "나의 GitHub 잔디 조회",
            description = "최근 3개월 기준으로 나의 GitHub 기여 캘린더(날짜별 기여 수, 기여 단계)를 조회합니다."
    )
    @GetMapping("/me/contribution-calendar")
    public ResponseEntity<ApiResult<ContributionCalendarResponse>> getMyContributionCalendar(
            HttpServletRequest request
    ) {
        Long appuserId = (Long) request.getAttribute("appuserId");
        ContributionCalendarResponse response = profileContributionService.getMyContributionCalendar(appuserId);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(summary = "전체 기술스택 조회")
    @GetMapping("/tech-stacks")
    public ResponseEntity<ApiResult<List<TechStackResponse>>> getAllTechStacks() {
        List<TechStackResponse> response = techStackService.getAllTechStacks();
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }

    @Operation(
            summary = "나의 기술스택 수정",
            description = "로그인한 사용자의 프로필 기술스택 목록을 수정하고 수정된 목록을 반환합니다."
    )
    @PutMapping("/me/tech-stacks")
    public ResponseEntity<ApiResult<List<TechStackResponse>>> updateMyTechStacks(
            HttpServletRequest request,
            @Valid @RequestBody UpdateMyTechStacksRequest updateTechStackRequest
    ) {
        Long appuserId = (Long) request.getAttribute("appuserId");
        List<TechStackResponse> response = techStackService.updateMyTechStacks(appuserId, updateTechStackRequest);
        return ApiResult.success(SuccessCode.UPDATED, response);
    }


    @Operation(
            summary = "나의 기술스택 조회",
            description = "로그인한 사용자의 프로필에 등록된 기술스택 목록을 조회합니다."
    )
    @GetMapping("/me/tech-stacks")
    public ResponseEntity<ApiResult<List<TechStackResponse>>> getMyTechStacks(
            HttpServletRequest request
    ) {
        Long appuserId = (Long) request.getAttribute("appuserId");
        List<TechStackResponse> response = techStackService.getMyTechStacks(appuserId);
        return ApiResult.success(SuccessCode.SUCCESS, response);
    }
}
