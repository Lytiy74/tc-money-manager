package org.tc.mtracker.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tc.mtracker.category.enums.CategoryStatus;
import org.tc.mtracker.common.enums.TransactionType;
import org.tc.mtracker.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("""
        SELECT c FROM Category c 
        WHERE (c.user IS NULL OR c.user = :user) 
        AND (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))) 
        AND (c.type IN :types)
                AND c.status = :status
    """)
    List<Category> findGlobalAndUserCategories(
            @Param("user") User user,
            @Param("name") String name,
            @Param("types") List<TransactionType> types,
            @Param("status") CategoryStatus status
    );

    @Query("""
        SELECT c from Category c
        WHERE (c.user = :user OR c.user IS NULL)
        AND LOWER(c.name) = LOWER(:name)
    """)
    List<Category> findAllByNameAndUser(String name, User user);

    @Query("""
                SELECT c FROM Category c
                WHERE c.id = :id
                AND (c.user = :user OR c.user IS NULL)
            """)
    Optional<Category> findAccessibleById(@Param("id") Long id, @Param("user") User user);

    @Query("""
                SELECT c FROM Category c
                WHERE c.id = :id
                AND c.user = :user
            """)
    Optional<Category> findOwnedById(@Param("id") Long id, @Param("user") User user);
}
