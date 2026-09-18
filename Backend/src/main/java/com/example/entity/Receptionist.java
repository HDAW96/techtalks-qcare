package com.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "receptionist")
public class Receptionist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receptionist_id", nullable = false)
    private Integer receptionistId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    public Receptionist() {
    }

    public Receptionist(User user) {
        this.user = user;
    }

    public Integer getReceptionistId() {
        return receptionistId;
    }

    public void setReceptionistId(Integer receptionistId) {
        this.receptionistId = receptionistId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
