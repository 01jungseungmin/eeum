package com.eeum.eeum.support;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CI에서 통합 테스트가 통째로 건너뛰어지는 것을 막는다.
 * <p>
 * 통합 테스트 29개는 {@code @EnabledIfDockerAvailable}로 Docker가 없으면 조용히 skip된다.
 * 로컬에서는 그게 편하지만, CI에서 그대로 두면 DB·동시성 검증이 하나도 돌지 않은 채
 * 빌드가 초록으로 끝난다. CI 환경에서는 Docker 부재 자체를 실패로 만든다.
 */
class IntegrationEnvironmentTest {

    @Test
    void CI에서는_Docker가_있어야_통합_테스트가_실행된다() {
        if (!"true".equalsIgnoreCase(System.getenv("CI"))) {
            return;   // 로컬 개발 환경은 그대로 건너뛴다
        }

        assertThat(DockerClientFactory.instance().isDockerAvailable())
                .as("CI에서 Docker를 쓸 수 없어 통합 테스트가 전부 skip된다")
                .isTrue();
    }
}
