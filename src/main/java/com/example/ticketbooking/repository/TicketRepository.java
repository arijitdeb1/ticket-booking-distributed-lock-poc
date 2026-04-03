package com.example.ticketbooking.repository;

import com.example.ticketbooking.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByEventIdAndSeatId(String eventId, String seatId);
}
