package com.ssafy.githubble.domain.ai.repository;

import com.ssafy.githubble.domain.ai.entity.CompareFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompareFeedbackRepository extends JpaRepository<CompareFeedbackEntity, UUID> {
}
