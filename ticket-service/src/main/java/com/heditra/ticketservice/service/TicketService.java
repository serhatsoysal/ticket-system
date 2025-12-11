package com.heditra.ticketservice.service;

import com.heditra.ticketservice.model.Ticket;
import com.heditra.ticketservice.model.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketService {

    Ticket createTicket(Ticket ticket);

    Ticket getTicketById(Long id);

    List<Ticket> getAllTickets();

    List<Ticket> getTicketsByUserId(Long userId);

    List<Ticket> getTicketsByStatus(TicketStatus status);

    List<Ticket> getTicketsByEventName(String eventName);

    Ticket updateTicketStatus(Long id, TicketStatus status);

    Ticket cancelTicket(Long id);

    void deleteTicket(Long id);
}

