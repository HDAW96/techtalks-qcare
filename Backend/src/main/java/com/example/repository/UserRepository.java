package com.example.repository;

import com.example.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByPhoneNumberAndFullName(
            String phoneNumber,
            String fullName
    );

    boolean existsByPhoneNumberAndFullName(
            String phoneNumber,
            String fullName
    );

    boolean existsByRole(String role);
}
