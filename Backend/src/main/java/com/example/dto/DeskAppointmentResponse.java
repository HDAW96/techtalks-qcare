package com.example.dto;

import com.example.entity.Appointment;

/**
 * An appointment as the reception desk sees it: everything a patient sees
 * about their own appointment, plus who the patient is. Only ever sent to
 * reception staff - a patient's list never carries other people's names.
 *
 * The phone number is here so the desk can call a patient to agree a new time.
 */
public class DeskAppointmentResponse extends AppointmentResponse {

    private Integer patientId;
    private String patientName;
    private String phoneNumber;

    public DeskAppointmentResponse() {
    }

    public static DeskAppointmentResponse of(Appointment appointment) {
        DeskAppointmentResponse dto = new DeskAppointmentResponse();
        dto.fill(appointment);
        dto.patientId = appointment.getPatient().getPatientId();
        dto.patientName = appointment.getPatient().getFullName();
        dto.phoneNumber = appointment.getPatient().getPhoneNumber();
        return dto;
    }

    public Integer getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
}
