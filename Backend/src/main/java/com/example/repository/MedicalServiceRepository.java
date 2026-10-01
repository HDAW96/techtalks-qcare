package com.example.repository;

import com.example.entity.MedicalService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalServiceRepository extends JpaRepository<MedicalService, Integer> {

    /** What patients and the desk may choose from: active services only. */
    List<MedicalService> findByActiveTrueOrderByServiceNameAsc();

    /** Every service, inactive included, for the admin's list. */
    List<MedicalService> findAllByOrderByServiceNameAsc();

    /** A duplicate name on create. */
    boolean existsByServiceNameIgnoreCase(String serviceName);

    /** A duplicate name on rename - the service itself does not count. */
    boolean existsByServiceNameIgnoreCaseAndServiceIdNot(String serviceName, Integer serviceId);
}
