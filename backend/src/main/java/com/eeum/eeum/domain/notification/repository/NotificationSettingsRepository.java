package com.eeum.eeum.domain.notification.repository;

import com.eeum.eeum.domain.notification.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

    Optional<NotificationSettings> findByAccount_AccountId(Long accountId);

    boolean existsByAccount_AccountId(Long accountId);

    void deleteByAccount_AccountId(Long accountId);

    @Query("SELECT s.account.accountId FROM NotificationSettings s WHERE s.marketingEnabled = true")
    List<Long> findAccountIdsByMarketingEnabled();

    //AI 메시지 발송 대상 중 마케팅 수신 동의 고객만 배치 필터링
    @Query("SELECT s.account.accountId FROM NotificationSettings s "
            + "WHERE s.marketingEnabled = true AND s.account.accountId IN :accountIds")
    List<Long> findMarketingEnabledAccountIds(List<Long> accountIds);
}
