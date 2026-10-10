package com.example.controller;

import com.example.dto.PatientSearchResponse;
import com.example.service.PatientSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientSearchController {

    private final PatientSearchService patientSearchService;

    public PatientSearchController(PatientSearchService patientSearchService) {
        this.patientSearchService = patientSearchService;
    }

     public List<PatientSearchResponse> search(@RequestParam(required = false) String search) {
        return patientSearchService.search(search);
    }
}
