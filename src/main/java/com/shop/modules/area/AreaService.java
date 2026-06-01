package com.shop.modules.area;

import com.shop.modules.area.dto.AreaResponse;
import com.shop.modules.area.dto.CreateAreaRequest;
import com.shop.modules.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AreaService {

    private final AreaRepository areaRepository;
    private final com.shop.modules.user.UserRepository userRepository;

    // Convert entity to DTO
    private AreaResponse toResponse(Area area) {
        return AreaResponse.builder()
                .id(area.getId())
                .name(area.getName())
                .description(area.getDescription())
                .salesmanId(area.getSalesman() != null ? area.getSalesman().getId() : null)
                .salesmanName(area.getSalesman() != null ? area.getSalesman().getName() : null)
                .salesmanPhone(area.getSalesman() != null ? area.getSalesman().getPhone() : null)
                .createdAt(area.getCreatedAt())
                .build();
    }

    public List<AreaResponse> getAllAreas() {
        return areaRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AreaResponse getAreaById(UUID id) {
        return toResponse(areaRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Area not found: " + id)));
    }

    public AreaResponse createArea(CreateAreaRequest req) {

        // Trim whitespace
        String name = req.getName().trim();

        // Check blank after trim
        if (name.isBlank()) {
            throw new RuntimeException(
                    "Area name cannot be blank or whitespace");
        }

        // Check duplicate name
        boolean exists = areaRepository.findAll()
                .stream()
                .anyMatch(a -> a.getName()
                        .equalsIgnoreCase(name));

        if (exists) {
            throw new RuntimeException(
                    "Area with name '"
                            + name
                            + "' already exists");
        }

        User salesman = null;
        if (req.getSalesmanId() != null) {
            salesman = userRepository.findById(req.getSalesmanId())
                    .orElseThrow(() -> new RuntimeException("Salesman not found: " + req.getSalesmanId()));
        }

        Area area = Area.builder()
                .name(name)
                .description(req.getDescription() != null
                        ? req.getDescription().trim() : null)
                .salesman(salesman)
                .build();

        return toResponse(areaRepository.save(area));
    }

    public AreaResponse updateArea(
            UUID id, CreateAreaRequest req) {

        Area area = areaRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Area not found: " + id));

        String name = req.getName().trim();

        if (name.isBlank()) {
            throw new RuntimeException(
                    "Area name cannot be blank");
        }

        // Check duplicate — exclude current
        boolean exists = areaRepository.findAll()
                .stream()
                .anyMatch(a -> a.getName()
                        .equalsIgnoreCase(name)
                        && !a.getId().equals(id));

        if (exists) {
            throw new RuntimeException(
                    "Area with name '"
                            + name
                            + "' already exists");
        }

        User salesman = null;
        if (req.getSalesmanId() != null) {
            salesman = userRepository.findById(req.getSalesmanId())
                    .orElseThrow(() -> new RuntimeException("Salesman not found: " + req.getSalesmanId()));
        }

        area.setName(name);
        area.setDescription(
                req.getDescription() != null
                        ? req.getDescription().trim() : null);
        area.setSalesman(salesman);

        return toResponse(areaRepository.save(area));
    }

    public void deleteArea(UUID id) {
        if (!areaRepository.existsById(id)) {
            throw new RuntimeException(
                    "Area not found: " + id);
        }
        areaRepository.deleteById(id);
    }
}