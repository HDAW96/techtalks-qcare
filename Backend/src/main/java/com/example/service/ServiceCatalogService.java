package com.example.service;

import com.example.dto.AdminServiceResponse;
import com.example.dto.ServiceRequestDTO;
import com.example.entity.MedicalService;
import com.example.repository.AppointmentRepository;
import com.example.repository.MedicalServiceRepository;
import com.example.repository.QueueTicketRepository;
import com.example.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The admin's list of treatments: create, edit, deactivate and delete.
 *
 * A service that any appointment or queue ticket refers to is never deleted,
 * only deactivated: deleting it would break that history. A deactivated
 * service stays readable everywhere it already appears, but cannot be chosen
 * for anything new - see requireActive.
 */
@Service
public class ServiceCatalogService {

    static final int MAX_NAME_LENGTH = 100;
    /** Eight hours. Long enough for any real treatment, short enough to catch a typo like 4500. */
    static final int MAX_MINUTES = 480;

    private final MedicalServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueTicketRepository ticketRepository;

    public ServiceCatalogService(MedicalServiceRepository serviceRepository,
            AppointmentRepository appointmentRepository,
            QueueTicketRepository ticketRepository) {
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Refuses a service that has been deactivated. Called wherever a patient
     * or the desk chooses a treatment for something new: the day picker,
     * booking, a desk walk-in and a QR join. Static so those services need no
     * new dependency for a one-line check.
     */
    public static void requireActive(MedicalService service) {
        if (!Boolean.TRUE.equals(service.getActive())) {
            throw new ApiException(HttpStatus.CONFLICT, "SERVICE_INACTIVE",
                    "That treatment is no longer offered. Please choose another.");
        }
    }

    /** Every service, inactive included, sorted by name. */
    @Transactional(readOnly = true)
    public List<AdminServiceResponse> list() {
        return serviceRepository.findAllByOrderByServiceNameAsc().stream()
                .map(service -> AdminServiceResponse.of(service, inUse(service)))
                .toList();
    }

    @Transactional
    public AdminServiceResponse create(ServiceRequestDTO request) {
        String name = validName(request.getName());
        int minutes = validMinutes(request.getMinutes());
        if (serviceRepository.existsByServiceNameIgnoreCase(name)) {
            throw duplicate();
        }
        MedicalService service = new MedicalService(name, minutes);
        if (request.getActive() != null) {
            service.setActive(request.getActive());
        }
        MedicalService saved = serviceRepository.save(service);
        return AdminServiceResponse.of(saved, false);
    }

    /** Changes only the fields that were sent; a null field is left as it is. */
    @Transactional
    public AdminServiceResponse update(Integer serviceId, ServiceRequestDTO request) {
        MedicalService service = serviceOrThrow(serviceId);

        if (request.getName() != null) {
            String name = validName(request.getName());
            if (serviceRepository.existsByServiceNameIgnoreCaseAndServiceIdNot(name, serviceId)) {
                throw duplicate();
            }
            service.setServiceName(name);
        }
        if (request.getMinutes() != null) {
            service.setDurationMinutes(validMinutes(request.getMinutes()));
        }
        if (request.getActive() != null) {
            service.setActive(request.getActive());
        }
        return AdminServiceResponse.of(service, inUse(service));
    }

    /** Removes a service that nothing has ever used. Anything else must be deactivated instead. */
    @Transactional
    public void delete(Integer serviceId) {
        MedicalService service = serviceOrThrow(serviceId);
        if (inUse(service)) {
            throw new ApiException(HttpStatus.CONFLICT, "SERVICE_IN_USE",
                    "This treatment is part of existing appointments, so it cannot be deleted. Deactivate it instead.");
        }
        serviceRepository.delete(service);
    }

    private boolean inUse(MedicalService service) {
        return appointmentRepository.existsByMedicalService(service)
                || ticketRepository.existsByMedicalService(service);
    }

    private MedicalService serviceOrThrow(Integer serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND",
                        "That treatment was not found."));
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED",
                    "Please enter the treatment's name.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_TOO_LONG",
                    "The name can be at most " + MAX_NAME_LENGTH + " characters.");
        }
        return name;
    }

    private static int validMinutes(Integer minutes) {
        if (minutes == null || minutes <= 0 || minutes > MAX_MINUTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DURATION_INVALID",
                    "The duration must be a whole number of minutes from 1 to " + MAX_MINUTES + ".");
        }
        return minutes;
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "SERVICE_EXISTS",
                "A treatment with that name already exists.");
    }
}
