package com.example.dto;

import java.util.List;

/**
 * The admin's analytics for one date range. `from` and `to` are echoed back
 * after defaulting so the page can print the range without date maths.
 *
 * `averageWaitMinutes` is null - not 0 - when nobody was called in during the
 * range, because 0 would read as "no waiting at all".
 */
public class AnalyticsResponse {

    private final String from;
    private final String to;
    private final AppointmentTotals appointments;
    private final long walkIns;
    private final Long averageWaitMinutes;
    private final List<DoctorPatients> patientsPerDoctor;
    private final List<ServiceCount> busiestServices;
    private final List<HourCount> busiestHours;

    public AnalyticsResponse(String from, String to, AppointmentTotals appointments, long walkIns,
            Long averageWaitMinutes, List<DoctorPatients> patientsPerDoctor,
            List<ServiceCount> busiestServices, List<HourCount> busiestHours) {
        this.from = from;
        this.to = to;
        this.appointments = appointments;
        this.walkIns = walkIns;
        this.averageWaitMinutes = averageWaitMinutes;
        this.patientsPerDoctor = patientsPerDoctor;
        this.busiestServices = busiestServices;
        this.busiestHours = busiestHours;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public AppointmentTotals getAppointments() {
        return appointments;
    }

    public long getWalkIns() {
        return walkIns;
    }

    public Long getAverageWaitMinutes() {
        return averageWaitMinutes;
    }

    public List<DoctorPatients> getPatientsPerDoctor() {
        return patientsPerDoctor;
    }

    public List<ServiceCount> getBusiestServices() {
        return busiestServices;
    }

    public List<HourCount> getBusiestHours() {
        return busiestHours;
    }

    /** total = completed + cancelled + noShow + upcoming. */
    public static class AppointmentTotals {
        private final long total;
        private final long completed;
        private final long cancelled;
        private final long noShow;
        private final long upcoming;

        public AppointmentTotals(long total, long completed, long cancelled, long noShow, long upcoming) {
            this.total = total;
            this.completed = completed;
            this.cancelled = cancelled;
            this.noShow = noShow;
            this.upcoming = upcoming;
        }

        public long getTotal() {
            return total;
        }

        public long getCompleted() {
            return completed;
        }

        public long getCancelled() {
            return cancelled;
        }

        public long getNoShow() {
            return noShow;
        }

        public long getUpcoming() {
            return upcoming;
        }
    }

    public static class DoctorPatients {
        private final Integer doctorId;
        private final String doctorName;
        private final long patients;

        public DoctorPatients(Integer doctorId, String doctorName, long patients) {
            this.doctorId = doctorId;
            this.doctorName = doctorName;
            this.patients = patients;
        }

        public Integer getDoctorId() {
            return doctorId;
        }

        public String getDoctorName() {
            return doctorName;
        }

        public long getPatients() {
            return patients;
        }
    }

    public static class ServiceCount {
        private final Integer serviceId;
        private final String name;
        private final long count;

        public ServiceCount(Integer serviceId, String name, long count) {
            this.serviceId = serviceId;
            this.name = name;
            this.count = count;
        }

        public Integer getServiceId() {
            return serviceId;
        }

        public String getName() {
            return name;
        }

        public long getCount() {
            return count;
        }
    }

    public static class HourCount {
        private final int hour;
        private final long count;

        public HourCount(int hour, long count) {
            this.hour = hour;
            this.count = count;
        }

        public int getHour() {
            return hour;
        }

        public long getCount() {
            return count;
        }
    }
}
