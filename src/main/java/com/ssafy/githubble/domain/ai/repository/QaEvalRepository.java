package com.ssafy.githubble.domain.ai.repository;

import com.ssafy.githubble.domain.ai.entity.QaEvalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QaEvalRepository extends JpaRepository<QaEvalEntity, UUID> {
}
