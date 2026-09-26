package com.ktalk.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * user_wrong_answer.problem_id 외래키를 ON DELETE CASCADE로 바꾼다. 커리큘럼 시더가 내용이 바뀌면
 * 기존 커리큘럼(문항 포함)을 지우고 다시 심는데, 오답노트 행이 문항을 참조하고 있으면 참조 무결성
 * 위반으로 앱이 부팅에 실패했다. 엔티티의 @OnDelete는 새로 만들어지는 스키마에만 적용되고
 * ddl-auto=update는 이미 있는 외래키를 고치지 않으므로, 다른 시더보다 먼저 돌며 기존 제약을 교체한다.
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class WrongAnswerSchemaMigration implements CommandLineRunner {

    private static final String FIND_NON_CASCADE_FK_SQL = """
            SELECT rc.constraint_name AS constraint_name
            FROM information_schema.referential_constraints rc
            JOIN information_schema.key_column_usage kcu
              ON rc.constraint_name = kcu.constraint_name
            WHERE LOWER(kcu.table_name) = 'user_wrong_answer'
              AND LOWER(kcu.column_name) = 'problem_id'
              AND UPPER(rc.delete_rule) <> 'CASCADE'
            """;

    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection()) {
            List<String> constraintNames = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(FIND_NON_CASCADE_FK_SQL)) {
                while (rs.next()) {
                    constraintNames.add(rs.getString("constraint_name"));
                }
            }
            for (String name : constraintNames) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE user_wrong_answer DROP CONSTRAINT \"" + name.replace("\"", "") + "\"");
                    st.execute("ALTER TABLE user_wrong_answer ADD CONSTRAINT fk_wrong_answer_problem "
                            + "FOREIGN KEY (problem_id) REFERENCES curriculum_problem (id) ON DELETE CASCADE");
                    System.out.println("✅ user_wrong_answer.problem_id 외래키를 ON DELETE CASCADE로 교체: " + name);
                }
            }
        } catch (SQLException e) {
            System.out.println("⚠ 오답노트 외래키 마이그레이션 실패 (무시하고 계속 진행): " + e.getMessage());
        }
    }
}
