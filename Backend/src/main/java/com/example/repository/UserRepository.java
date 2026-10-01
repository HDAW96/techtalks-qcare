package com.example.repository;

import com.example.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Sign-in lookup. The phone alone is not unique because a family shares
     * one number, so the name is part of the key.
     */
    Optional<User> findByPhoneNumberAndFullName(String phoneNumber, String fullName);

    boolean existsByPhoneNumberAndFullName(String phoneNumber, String fullName);

    /**
     * Used by the seeder to decide whether the receptionist accounts already
     * exist. They need their own check because the doctor block only runs on
     * an empty database.
     */
    boolean existsByRole(String role);
    @Query("""
    SELECT u FROM User u
    WHERE (:role IS NULL OR u.role = :role)
      AND (
          :search IS NULL
          OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
          OR u.phoneNumber LIKE CONCAT('%', :search, '%')
      )
""")
List<User> searchUsers(
        @Param("role") String role,
        @Param("search") String search
);


}
