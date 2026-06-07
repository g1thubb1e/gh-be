package com.ssafy.githubble.domain.profile.service;

import com.ssafy.githubble.global.properties.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3PresignedUrlService {

    private final S3Presigner presigner;
    private final S3Properties s3Properties;


    public String createGetUrl(String key) {
        // DB bucket + 아이콘 key로 어떤 파일을 가져올지 요청할 객체 만들기
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .build();

        // 만료시간 30분 설정
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(30))
                .getObjectRequest(getObjectRequest)
                .build();

        // 30분 동안 유효한, 해당 S3 객체 다운로드용 임시 URL 문자열
        return presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
}
