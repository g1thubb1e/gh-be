package com.ssafy.githubble.domain.ai.repository;

import com.ssafy.githubble.domain.ai.entity.RatingFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RatingFeedbackRepository extends JpaRepository<RatingFeedbackEntity, UUID> {
}
