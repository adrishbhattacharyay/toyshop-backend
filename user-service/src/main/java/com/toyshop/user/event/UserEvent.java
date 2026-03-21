package com.toyshop.user.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {
    private Long userId;
    private String username;
    private String email;
    private UserEventType eventType;
    private LocalDateTime timestamp;
    private String metadata;
}