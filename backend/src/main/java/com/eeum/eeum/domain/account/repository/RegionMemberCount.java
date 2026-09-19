package com.eeum.eeum.domain.account.repository;

/** 시·도 + 구·군 단위 회원 수 집계 원값. */
public record RegionMemberCount(
        String siDo,
        String gunGu,
        Long memberCount
) {
}
