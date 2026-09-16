package com.eeum.eeum.domain.ai.repository;

import com.eeum.eeum.domain.ai.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    @Modifying
    @Query("DELETE FROM AiChatMessage m WHERE m.createdAt < :threshold")
    int deleteOldMessages(@Param("threshold") LocalDateTime threshold);
}
