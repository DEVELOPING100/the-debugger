package ca.unb.labline.repository;

import ca.unb.labline.model.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByCourseIdAndActiveTrue(Long courseId);
}
