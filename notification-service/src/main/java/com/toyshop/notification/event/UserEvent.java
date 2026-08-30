package com.toyshop.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEvent {
    private Long userId;
    private String username;
    private String email;
    private String eventType;
    private LocalDateTime timestamp;
}
