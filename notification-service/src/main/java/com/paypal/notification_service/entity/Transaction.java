package com.paypal.notification_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;


@Entity
@Table(name = "transaction")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false)
    private Long receiverId;


    @Column(nullable = false)
    @Positive(message = "Amount must be positive")
    private Double amount;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String status;

    public Transaction() {}



    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public Long getSenderId() {
        return senderId;
    }
    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }
    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public Double getAmount() {
        return amount;
    }
    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", senderId=" + senderId +
                ", receiverId=" + receiverId +
                ", amount=" + amount +
                ", timestamp=" + timestamp +
                ", status='" + status + '\'' +
                '}';
    }
}
