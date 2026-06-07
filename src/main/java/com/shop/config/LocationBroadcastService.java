package com.shop.config;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Broadcasts real-time delivery boy location updates to all
 * WebSocket clients subscribed to /topic/location/{deliveryBoyId}
 */
@Service
@RequiredArgsConstructor
public class LocationBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Called whenever a delivery boy's GPS location is updated.
     * Pushes the new coordinates to all admin/manager clients watching that delivery boy.
     */
    public void broadcastLocation(UUID userId, String userName, Double latitude, Double longitude) {
        LocationUpdate update = new LocationUpdate(userId.toString(), userName, latitude, longitude, System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/location/" + userId, update);
    }

    /** DTO sent over WebSocket to clients */
    public record LocationUpdate(
        String userId,
        String userName,
        Double latitude,
        Double longitude,
        long timestamp
    ) {}
}
