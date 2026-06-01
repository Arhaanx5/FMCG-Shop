package com.shop.modules.area;

import com.shop.common.ApiResponse;
import com.shop.modules.area.dto.AreaResponse;
import com.shop.modules.area.dto.CreateAreaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<AreaResponse>>>
    getAll() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        areaService.getAllAreas()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<AreaResponse>>
    getById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        areaService.getAreaById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AreaResponse>>
    create(@Valid @RequestBody
           CreateAreaRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Area created successfully",
                        areaService.createArea(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AreaResponse>>
    update(@PathVariable UUID id,
           @Valid @RequestBody
           CreateAreaRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Area updated successfully",
                        areaService.updateArea(id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>>
    delete(@PathVariable UUID id) {
        areaService.deleteArea(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Area deleted", null));
    }
}