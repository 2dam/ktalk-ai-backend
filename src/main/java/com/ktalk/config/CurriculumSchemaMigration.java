package com.ktalk.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 급수단계별 Curriculum 분리(1~2급/3~4급/5~6급)를 도입하면서 {@code curriculum} 테이블의
 * 유니크 제약을 {@code learner_type} 단일 컬럼에서 {@code (learner_type, target_level_from)}
 * 복합 컬럼으로 바꿨다. {@code spring.jpa.hibernate.ddl-auto=update}는 새 제약을 추가는 하지만
 * 예전 단일 컬럼 제약은 자동으로 지우지 않으므로, 그 제약이 남아있으면 같은 학습유형의 두 번째
 * 급수단계 Curriculum을 저장할 때 유니크 제약 위반이 난다. 그래서 부팅 시 다른 커리큘럼
 * 시더보다 먼저 실행되어, {@code learner_type} 한 컬럼짜리 예전 유니크 제약을 찾아 지운다.
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CurriculumSchemaMigration implements CommandLineRunner {

    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        String findLegacyConstraintsSql = """
                SELECT tc.constraint_name AS constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_name = kcu.table_name
                WHERE tc.constraint_type = 'UNIQUE'
                  AND LOWER(tc.table_name) = 'curriculum'
                GROUP BY tc.constraint_name
                HAVING COUNT(*) = 1
                   AND MAX(LOWER(kcu.column_name)) = 'learner_type'
                """;

        List<String> legacyConstraintNames = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(findLegacyConstraintsSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                legacyConstraintNames.add(rs.getString("constraint_name"));
            }
        } catch (SQLException e) {
            System.out.println("⚠ curriculum 테이블 예전 유니크 제약 조회에 실패했습니다 (무시하고 계속 진행): " + e.getMessage());
            return;
        }

        for (String constraintName : legacyConstraintNames) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE curriculum DROP CONSTRAINT \"" + constraintName + "\"");
                System.out.println("🔧 curriculum." + "learner_type 단일 유니크 제약 제거 완료: " + constraintName);
            } catch (SQLException e) {
                System.out.println("⚠ curriculum 예전 유니크 제약(" + constraintName + ") 제거 실패 (무시하고 계속 진행): " + e.getMessage());
            }
        }
    }
}
