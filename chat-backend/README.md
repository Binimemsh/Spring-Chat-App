# 🚀 Real-Time Chat Backend Application

A secure and scalable real-time chat backend built using **Spring Boot**, **Spring Security**, **JWT authentication**, and **WebSocket (STOMP protocol)**.

This project demonstrates enterprise-level backend architecture, secure authentication, and real-time communication design.

---

## 📌 Overview

This application provides:

- 🔐 JWT-based authentication (Access + Refresh tokens)
- 💬 Real-time messaging via WebSocket
- 🏠 Chat room management
- ✍️ Typing notifications
- ✅ Message read receipts
- 🗄 Persistent message storage
- 🏗 Clean layered backend architecture

It combines REST APIs with WebSocket messaging to create a secure and scalable chat backend system.

---

## 🏗 Architecture Design

The system follows a clean layered architecture:

Controller Layer

    ↓
Service Layer

    ↓
Repository Layer

     ↓
Database


## Additional layers:
- Security Layer (JWT + Spring Security)
- WebSocket Configuration Layer
- DTO Abstraction Layer

This separation ensures maintainability, scalability, and testability.

---

## 🛠 Technology Stack

- Java
- Spring Boot
- Spring Security
- JWT (JSON Web Tokens)
- WebSocket (STOMP Protocol)
- Spring Data JPA
- MySQL / H2 Database(i have used H2 in development time)
- Maven

---

## 📂 Project Structure

#com.chat

  config
  
    CorsConfig.java
    SecurityConfig.java
    SecurityContextFilter.java
    WebSocketConfig.java
    WebSocketAuthInterceptor.java
    WebSocketSecurityConfig.java

  controller
  
    AuthController.java
    ChatController.java
    EnhancedChatController.java
    RestChatController.java
    UserController.java
    
  dto
  
    ApiResponse.java
    ChatMessageDTO.java
    LoginRequest.java
    RegisterRequest.java
    RefreshTokenRequest.java
    TypingNotificationDTO.java
    UserDTO.java
    UserStatusDTO.java
 model
 
    User.java
    ChatMessage.java
    ChatRoom.java
repository

     UserRepository.java
     ChatMessageRepository.java
     ChatRoomRepository.java
security

    JwtAuthenticationFilter.java
     UserDetailsServiceImpl.java
service

    ChatService.java
    JwtService.java

---


---

## 🔐 Authentication & Security

- Stateless authentication using JWT
- Access token & refresh token mechanism
- Custom `JwtAuthenticationFilter`
- Secure WebSocket authentication interceptor
- Role-based authorization
- Spring Security configuration
- SecurityContext integration

---

## 💬 Real-Time Messaging Features

- WebSocket endpoint configuration
- STOMP messaging protocol
- Secure message broadcasting
- Chat room-based communication
- Typing notifications
- Read receipt tracking
- Message persistence in database

---

## 🌐 REST API Endpoints

### 🔑 Authentication

**| Method | Endpoint |     Description |**


**| POST   | /auth/register | Register a new user  |**

**| POST   | /auth/login    | Authenticate user    |**

**| POST   | /auth/refresh  | Refresh access token |**

---
### 👤 Users & Chat

**| Method | Endpoint | Description |**


**| GET    | /users | Retrieve all users |**

**| GET    | /chat/history/{roomId} | Get chat history |**

**| GET    | /chat/rooms | Get chat rooms |**




---

## 🗄 Database Design

### Core Entities

- **User**
- **ChatRoom**
- **ChatMessage**

### Relationships

- A user can belong to multiple chat rooms
- A chat room contains multiple messages
- Messages are linked to sender and room

---

## ▶️ How To Run The Project

1. Clone the repository

2. Open the project in IntelliJ IDEA

3. Configure database in:

4. Run:


5. Test APIs using:
   - Postman (for REST endpoints)
   - WebSocket client or frontend (for real-time messaging)

---

## 🧠 Learning Outcomes

This project demonstrates:

- Advanced Spring Security configuration
- JWT lifecycle management
- Custom authentication filters
- WebSocket authentication integration
- DTO-based API design
- Clean layered backend architecture
- Secure real-time communication system design

---

## 🚀 Future Improvements

- Docker containerization
- Cloud deployment (AWS / Azure)
- Push notifications
- Message encryption
- Presence tracking (online/offline)
- Microservice-based architecture
- Monitoring & logging integration

---

## 📈 Project Purpose

This project was developed to practice enterprise-level backend engineering concepts, including:

- Secure authentication
- Real-time systems
- Scalable architecture
- Clean code structure
- Maintainable backend design

---

## 👤 Author

**Biniam**  
Computer Science Student | Backend Developer

---

⭐ If you found this project useful, consider giving it a star.




