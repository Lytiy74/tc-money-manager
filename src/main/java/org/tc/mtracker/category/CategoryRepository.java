package org.tc.mtracker.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tc.mtracker.category.enums.CategoryType;
import org.tc.mtracker.user.User;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("""
        SELECT c FROM Category c 
        WHERE (c.user IS NULL OR c.user = :user) 
        AND (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))) 
        AND (c.type IN :types)
    """)
    List<Category> findGlobalAndUserCategories(
            @Param("user") User user,
            @Param("name") String name,
            @Param("types") List<CategoryType> types
    );
}
