package com.example.dto;

import java.util.List;

/**
 * One bookable day: every start time on the clinic's half-hour grid, each
 * marked open or not, plus how many are open.
 *
 * Shaped to match what the booking screen already reads, so the time picker
 * and the "times free" count cannot disagree - they read the same list.
 */
public class DayAvailabilityResponse {

    private String date;
    private List<Slot> slots;
    private int openCount;

    public DayAvailabilityResponse() {
    }

    public DayAvailabilityResponse(String date, List<Slot> slots) {
        this.date = date;
        this.slots = slots;
        this.openCount = (int) slots.stream().filter(Slot::isOpen).count();
    }

    public String getDate() {
        return date;
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public int getOpenCount() {
        return openCount;
    }

    public static class Slot {
        private String time;
        private boolean open;

        public Slot() {
        }

        public Slot(String time, boolean open) {
            this.time = time;
            this.open = open;
        }

        public String getTime() {
            return time;
        }

        public boolean isOpen() {
            return open;
        }
    }
}
