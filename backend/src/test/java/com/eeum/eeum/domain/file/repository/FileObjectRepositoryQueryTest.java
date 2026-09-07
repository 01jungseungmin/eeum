package com.eeum.eeum.domain.file.repository;

import com.eeum.eeum.domain.file.entity.FileObject;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 증상: Repository 파생 쿼리 생성 실패로 서버가 기동하지 않는다.
 * 결함 위치: FileObjectRepository의 잠금 조회 메서드명.
 * 실제 Spring Data 파서로 모든 선언 메서드의 속성 경로 해석 성공을 고정한다.
 */
class FileObjectRepositoryQueryTest {

    @Test
    void 모든_파생_쿼리는_실제_엔티티_속성으로_해석된다() {
        for (Method method : FileObjectRepository.class.getDeclaredMethods()) {
            // @Query로 직접 쓴 쿼리와 default 메서드는 이름으로 파싱하지 않는다.
            // 걸러내지 않으면 그런 메서드가 추가되는 순간 무관한 실패가 난다.
            if (method.isAnnotationPresent(Query.class) || method.isDefault()) {
                continue;
            }
            assertThatCode(() -> new PartTree(method.getName(), FileObject.class))
                    .as(method.getName()).doesNotThrowAnyException();
        }
    }
}
