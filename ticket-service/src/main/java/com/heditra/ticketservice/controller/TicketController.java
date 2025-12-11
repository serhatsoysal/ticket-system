package com.heditra.ticketservice.controller;

import com.heditra.ticketservice.model.Ticket;
import com.heditra.ticketservice.model.TicketStatus;
import com.heditra.ticketservice.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Ticket Management", description = "APIs for managing ticket bookings")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @Operation(summary = "Create a new ticket", description = "Books a new ticket for an event")
    @ApiResponse(responseCode = "201", description = "Ticket created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid ticket data")
    public ResponseEntity<Ticket> createTicket(@Valid @RequestBody Ticket ticket) {
        Ticket createdTicket = ticketService.createTicket(ticket);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTicket);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by ID", description = "Retrieves a specific ticket by its ID")
    @ApiResponse(responseCode = "200", description = "Ticket found")
    @ApiResponse(responseCode = "404", description = "Ticket not found")
    public ResponseEntity<Ticket> getTicketById(
            @Parameter(description = "Ticket ID") @PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id);
        return ResponseEntity.ok(ticket);
    }

    @GetMapping
    @Operation(summary = "Get all tickets", description = "Retrieves all tickets in the system")
    public ResponseEntity<List<Ticket>> getAllTickets() {
        List<Ticket> tickets = ticketService.getAllTickets();
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get tickets by user", description = "Retrieves all tickets for a specific user")
    public ResponseEntity<List<Ticket>> getTicketsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        List<Ticket> tickets = ticketService.getTicketsByUserId(userId);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get tickets by status", description = "Retrieves all tickets with a specific status")
    public ResponseEntity<List<Ticket>> getTicketsByStatus(
            @Parameter(description = "Ticket status") @PathVariable TicketStatus status) {
        List<Ticket> tickets = ticketService.getTicketsByStatus(status);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/event/{eventName}")
    @Operation(summary = "Get tickets by event", description = "Retrieves all tickets for a specific event")
    public ResponseEntity<List<Ticket>> getTicketsByEventName(
            @Parameter(description = "Event name") @PathVariable String eventName) {
        List<Ticket> tickets = ticketService.getTicketsByEventName(eventName);
        return ResponseEntity.ok(tickets);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update ticket status", description = "Updates the status of a specific ticket")
    public ResponseEntity<Ticket> updateTicketStatus(
            @Parameter(description = "Ticket ID") @PathVariable Long id, 
            @Parameter(description = "New status") @RequestParam TicketStatus status) {
        Ticket updatedTicket = ticketService.updateTicketStatus(id, status);
        return ResponseEntity.ok(updatedTicket);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel ticket", description = "Cancels a specific ticket and releases seats")
    public ResponseEntity<Ticket> cancelTicket(
            @Parameter(description = "Ticket ID") @PathVariable Long id) {
        Ticket cancelledTicket = ticketService.cancelTicket(id);
        return ResponseEntity.ok(cancelledTicket);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete ticket", description = "Permanently deletes a ticket")
    @ApiResponse(responseCode = "204", description = "Ticket deleted successfully")
    public ResponseEntity<Void> deleteTicket(
            @Parameter(description = "Ticket ID") @PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}

