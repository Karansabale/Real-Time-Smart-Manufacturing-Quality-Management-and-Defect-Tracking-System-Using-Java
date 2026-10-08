package com.project.qms.repository;

import com.project.qms.entity.Role;
import com.project.qms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for users. Spring Data writes the SQL from the method names,
 * so no query code is needed for any of these.
 */
public interface UserRepository extends JpaRepository<User, Integer> {

    /** Used by login (FR-01.1) and by the duplicate check (FR-02.3). */
    Optional<User> findByUsername(String username);

    /** Used to reject a duplicate username before insert (FR-02.3). */
    boolean existsByUsername(String username);

    /** Active users only - the list used when selecting a responsible person (FR-08.3). */
    List<User> findByIsActiveTrueOrderByFullNameAsc();

    /** Every user, active or not, for the Admin's user list (FR-02.5). */
    List<User> findAllByOrderByFullNameAsc();

    long countByRole(Role role);
}
