package com.shop.modules.delivery;

import com.shop.modules.customer.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteOptimizationService {

    private final DeliveryRepository deliveryRepository;
    private final com.shop.modules.user.UserRepository userRepository;

    /**
     * Calculates the great-circle distance between two GPS coordinates
     * using the Haversine formula. Returns distance in kilometres.
     */
    public double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Builds the optimized route for a delivery boy.
     * Strategy: Area-based clustering + Nearest Neighbor within each area.
     */
    public RouteResult optimizeRoute(UUID deliveryBoyId) {
        com.shop.modules.user.User boy = userRepository.findById(deliveryBoyId).orElse(null);
        Double startLat = (boy != null && boy.getLastLatitude() != null) ? boy.getLastLatitude() : null;
        Double startLng = (boy != null && boy.getLastLongitude() != null) ? boy.getLastLongitude() : null;

        List<Delivery> deliveries = deliveryRepository.findByDeliveryBoyIdAndStatusIn(
                deliveryBoyId,
                List.of(DeliveryStatus.PENDING, DeliveryStatus.PACKED));

        // Build RouteStop objects and group by area
        Map<String, List<RouteStop>> areaGroups = new LinkedHashMap<>();

        for (Delivery d : deliveries) {
            Customer c = d.getBill().getCustomer();
            String areaName = (c.getArea() != null) ? c.getArea().getName() : "Unassigned";

            RouteStop stop = RouteStop.builder()
                    .deliveryId(d.getId())
                    .billNumber(d.getBill().getBillNumber())
                    .customerName(c.getName())
                    .shopName(c.getShopName())
                    .phone(c.getPhone())
                    .latitude(c.getLatitude())
                    .longitude(c.getLongitude())
                    .areaName(areaName)
                    .amountDue(d.getBill().getPendingAmount())
                    .status(d.getStatus().name())
                    .hasLocation(c.getLatitude() != null && c.getLongitude() != null)
                    .build();

            areaGroups.computeIfAbsent(areaName, k -> new ArrayList<>()).add(stop);
        }

        // Sort each area group using Nearest Neighbor, and build area groups
        List<RouteAreaGroup> sortedGroups = new ArrayList<>();
        double totalDistance = 0;
        int stopNumber = 1;

        for (Map.Entry<String, List<RouteStop>> entry : areaGroups.entrySet()) {
            List<RouteStop> stops = entry.getValue();

            // Separate stops with GPS from those without
            List<RouteStop> withGps = stops.stream()
                    .filter(RouteStop::isHasLocation)
                    .collect(Collectors.toList());
            List<RouteStop> withoutGps = stops.stream()
                    .filter(s -> !s.isHasLocation())
                    .collect(Collectors.toList());

            // Apply Nearest Neighbor to stops with GPS
            List<RouteStop> sorted = nearestNeighborSort(withGps, startLat, startLng);

            // Append no-GPS stops at the end
            sorted.addAll(withoutGps);

            // Assign stop numbers and accumulate distances
            for (RouteStop s : sorted) {
                s.setStopNumber(stopNumber++);
                totalDistance += s.getDistanceFromPreviousKm();
            }

            RouteAreaGroup group = RouteAreaGroup.builder()
                    .areaName(entry.getKey())
                    .stopCount(sorted.size())
                    .stops(sorted)
                    .build();
            sortedGroups.add(group);
        }

        return RouteResult.builder()
                .deliveryBoyId(deliveryBoyId)
                .totalStops(stopNumber - 1)
                .totalDistanceKm(Math.round(totalDistance * 100.0) / 100.0)
                .areaGroups(sortedGroups)
                .build();
    }

    /**
     * Sorts a list of stops using the Nearest Neighbor heuristic.
     * Starts from the first stop, always visits the closest unvisited next.
     */
    private List<RouteStop> nearestNeighborSort(List<RouteStop> stops, Double startLat, Double startLng) {
        if (stops.isEmpty())
            return stops;

        List<RouteStop> unvisited = new ArrayList<>(stops);
        List<RouteStop> sorted = new ArrayList<>();

        RouteStop current = null;
        double minDistToStart = Double.MAX_VALUE;

        // If starting position is available, find the closest stop to start with
        if (startLat != null && startLng != null) {
            for (RouteStop candidate : unvisited) {
                double dist = haversine(startLat, startLng, candidate.getLatitude(), candidate.getLongitude());
                if (dist < minDistToStart) {
                    minDistToStart = dist;
                    current = candidate;
                }
            }
            unvisited.remove(current);
            current.setDistanceFromPreviousKm(Math.round(minDistToStart * 100.0) / 100.0);
            sorted.add(current);
        } else {
            // Fallback: start with the first stop
            current = unvisited.remove(0);
            current.setDistanceFromPreviousKm(0);
            sorted.add(current);
        }

        while (!unvisited.isEmpty()) {
            RouteStop nearest = null;
            double minDist = Double.MAX_VALUE;

            for (RouteStop candidate : unvisited) {
                double dist = haversine(
                        current.getLatitude(), current.getLongitude(),
                        candidate.getLatitude(), candidate.getLongitude());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = candidate;
                }
            }

            nearest.setDistanceFromPreviousKm(Math.round(minDist * 100.0) / 100.0);
            sorted.add(nearest);
            unvisited.remove(nearest);
            current = nearest;
        }

        return sorted;
    }

    // ─── DTOs ────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteResult {
        private UUID deliveryBoyId;
        private int totalStops;
        private double totalDistanceKm;
        private List<RouteAreaGroup> areaGroups;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteAreaGroup {
        private String areaName;
        private int stopCount;
        private List<RouteStop> stops;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteStop {
        private UUID deliveryId;
        private String billNumber;
        private String customerName;
        private String shopName;
        private String phone;
        private Double latitude;
        private Double longitude;
        private String areaName;
        private BigDecimal amountDue;
        private String status;
        private boolean hasLocation;
        private int stopNumber;
        private double distanceFromPreviousKm;
    }
}
