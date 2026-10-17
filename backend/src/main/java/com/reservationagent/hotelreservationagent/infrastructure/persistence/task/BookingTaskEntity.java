package com.reservationagent.hotelreservationagent.infrastructure.persistence.task;

import com.reservationagent.hotelreservationagent.domain.task.AuthorizationMode;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.task.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "booking_tasks")
class BookingTaskEntity {

    @Id
    UUID id;
    String requestId;
    UUID municipalityId;
    LocalDate checkIn;
    LocalDate checkOut;
    int rooms;
    int adults;
    int children;
    String destinationEmail;
    @Enumerated(EnumType.STRING)
    AuthorizationMode authorizationMode;
    BigDecimal maximumTotal;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    String[] requiredAmenities;
    boolean allowCompetingQuoteDisclosure;
    String originalPrompt;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    String[] statedFields;
    String emailSource;
    String notes;
    BigDecimal maximumPerNight;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    String[] assumptions;
    Instant deadline;
    @Enumerated(EnumType.STRING)
    TaskStatus status;
    @Enumerated(EnumType.STRING)
    TaskOutcome outcome;
    Instant createdAt;
    Instant updatedAt;
    @Version
    long version;

    protected BookingTaskEntity() {
    }
}
