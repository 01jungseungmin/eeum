package com.eeum.eeum.domain.file.repository;

/**
 * 잠금 대상의 PK만 먼저 고를 때 쓰는 projection이다.
 * 엔티티를 먼저 영속성 컨텍스트에 올리면 뒤의 잠금 조회가 최신 상태를 보장하기 어려워진다.
 */
public interface FileObjectIdProjection {

    Long getFileObjectId();
}
