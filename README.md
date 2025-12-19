# Ticketing System

A microservices-based distributed ticketing system built with Spring Boot, implementing event-driven architecture and Saga pattern for distributed transaction management.

## Project Overview

This system manages ticket booking operations across multiple services, ensuring consistency and reliability through event-driven communication and distributed transaction orchestration.

## Architecture

### Microservices

- **API Gateway** (Port 8080): Single entry point for all client requests, routing to appropriate services
- **Ticket Service** (Port 8081): Manages ticket creation, updates, and cancellations using CQRS pattern
- **User Service** (Port 8082): Handles user management operations
- **Payment Service** (Port 8083): Processes payment transactions
- **Notification Service** (Port 8084): Sends notifications for various events
- **Inventory Service** (Port 8085): Manages event inventory and seat reservations
- **Saga Orchestrator Service** (Port 8086): Orchestrates distributed transactions using Saga pattern

### Infrastructure Components

- **Kafka**: Event streaming platform for asynchronous communication
- **MySQL**: Relational database for persistent storage
- **Redis**: Caching and distributed locking
- **Elasticsearch**: Search and analytics
- **Consul**: Service discovery and configuration
- **Prometheus**: Metrics collection
- **Grafana**: Metrics visualization
- **Nginx**: Load balancer and reverse proxy

## Project Logic

### Ticket Booking Flow

1. **Ticket Creation**: User creates a ticket through Ticket Service
   - Inventory Service reserves seats
   - Ticket is created with PENDING status
   - TicketCreatedEvent is published

2. **Saga Orchestration**: Saga Orchestrator receives TicketCreatedEvent
   - Tracks saga instance and steps
   - Coordinates payment and notification processes

3. **Payment Processing**: Payment Service listens to TicketCreatedEvent
   - Creates payment record
   - Processes payment
   - Publishes PaymentCompletedEvent or PaymentFailedEvent

4. **Ticket Confirmation**: Ticket Service listens to PaymentCompletedEvent
   - Updates ticket status to CONFIRMED

5. **Notification**: Notification Service listens to events
   - Sends notifications for user creation, ticket booking, and payment completion

6. **Compensation**: If any step fails, Saga Orchestrator triggers compensation
   - Releases reserved inventory
   - Cancels ticket
   - Refunds payment if processed

### Event-Driven Communication

Services communicate asynchronously through Kafka topics:
- `ticket-created`: Published when ticket is created
- `ticket-confirmed`: Published when ticket is confirmed
- `ticket-cancelled`: Published when ticket is cancelled
- `payment-initiated`: Published when payment is initiated
- `payment-completed`: Published when payment succeeds
- `payment-failed`: Published when payment fails
- `user-created`: Published when user is created

### CQRS Pattern

Ticket Service implements Command Query Responsibility Segregation:
- Commands: CreateTicketCommand, CancelTicketCommand, UpdateTicketStatusCommand
- Queries: Separate read models for optimized querying

### Resilience Patterns

- **Circuit Breaker**: Protects against cascading failures
- **Retry**: Automatic retry for transient failures
- **Distributed Locking**: Redis-based locking for inventory operations
- **Caching**: Redis caching for frequently accessed data

## How to Run

### Prerequisites

- Docker and Docker Compose
- Java 17 or higher
- Maven 3.6+

### Running with Docker Compose

1. Clone the repository:
```bash
git clone <repository-url>
cd ticketing-system
```

2. Start all services:
```bash
docker-compose up -d
```

3. Wait for all services to be healthy (check logs):
```bash
docker-compose logs -f
```

4. Access services:
   - API Gateway: http://localhost:8080
   - Consul UI: http://localhost:8500
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3000 (admin/admin)
   - Kibana: http://localhost:5601

### Running Individual Services

1. Start infrastructure services first:
```bash
docker-compose up -d consul mysql kafka redis elasticsearch prometheus grafana
```

2. Build and run each service:
```bash
cd <service-directory>
mvn clean install
mvn spring-boot:run
```

### Database Setup

Databases are automatically created on first startup via `init-db.sql`:
- ticketdb
- userdb
- paymentdb
- notificationdb
- inventorydb

### Service Endpoints

- **Tickets**: http://localhost:8080/tickets/**
- **Users**: http://localhost:8080/users/**
- **Payments**: http://localhost:8080/payments/**
- **Notifications**: http://localhost:8080/notifications/**
- **Inventory**: http://localhost:8080/inventory/**

### Health Checks

All services expose health endpoints:
- http://localhost:8080/actuator/health
- http://localhost:8081/actuator/health
- http://localhost:8082/actuator/health
- http://localhost:8083/actuator/health
- http://localhost:8084/actuator/health
- http://localhost:8085/actuator/health
- http://localhost:8086/actuator/health

### Stopping Services

```bash
docker-compose down
```

To remove volumes:
```bash
docker-compose down -v
```
