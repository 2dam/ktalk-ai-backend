package domain.content.repository;

import com.ktalk.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {

    Optional<Content> findByYoutubeId(String youtubeId);

    boolean existsByYoutubeId(String youtubeId);

    List<Content> findByCategoryAndStatus(String category, Content.ContentStatus status);

    @Query("SELECT c FROM Content c WHERE c.category = :category AND c.status = 'ACTIVE' ORDER BY c.createdAt DESC")
    List<Content> findLatestByCategory(@Param("category") String category);
}