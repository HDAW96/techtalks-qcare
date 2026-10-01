package com.example.dto;

/**
 * The body of a create or edit from the admin's services page. Every field is
 * optional so one class serves both: create requires name and minutes, while
 * an edit leaves any null field unchanged.
 *
 * The setters are required - without them Spring leaves every field null and
 * reports no error.
 */
public class ServiceRequestDTO {

    private String name;
    private Integer minutes;
    private Boolean active;

    public ServiceRequestDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public void setMinutes(Integer minutes) {
        this.minutes = minutes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
