package com.example.repository;
import com.example.entity.MedicalService;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MedicalServiceRepository extends JpaRepository<MedicalService, Integer> {
    
}
