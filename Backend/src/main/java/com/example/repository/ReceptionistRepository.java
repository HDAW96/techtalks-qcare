package com.example.repository;

import com.example.entity.Receptionist;
import com.example.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceptionistRepository extends JpaRepository<Receptionist, Integer> {

  Optional<Receptionist> findByUser(User user);
}
