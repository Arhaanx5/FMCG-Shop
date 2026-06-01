package com.shop.modules.delivery;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;

    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    public List<Delivery> getBoyDeliveriesToday(UUID boyId) {
        return deliveryRepository
            .findByDeliveryBoyAndDate(boyId, LocalDate.now());
    }

    @Transactional
    public Delivery assignDelivery(AssignDeliveryRequest req) {
        Bill bill = billRepository
                .findById(req.getBillId())
                .orElseThrow(() ->
                    new RuntimeException("Bill not found"));

        User boy = req.getDeliveryBoyId() != null
                ? userRepository
                    .findById(req.getDeliveryBoyId())
                    .orElse(null)
                : null;

        Delivery delivery = Delivery.builder()
                .bill(bill)
                .deliveryBoy(boy)
                .type(req.getType())
                .scheduledDate(req.getScheduledDate())
                .status(DeliveryStatus.PENDING)
                .cashCollected(BigDecimal.ZERO)
                .build();

        return deliveryRepository.save(delivery);
    }

    public Delivery updateStatus(UUID id, DeliveryStatus status) {
        Delivery delivery = deliveryRepository
                .findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Delivery not found"));

        delivery.setStatus(status);

        if (status == DeliveryStatus.OUT) {
            delivery.setDispatchedAt(LocalDateTime.now());
        }
        if (status == DeliveryStatus.DELIVERED) {
            delivery.setDeliveredAt(LocalDateTime.now());
        }

        return deliveryRepository.save(delivery);
    }

    public Delivery collectCash(UUID id, BigDecimal amount) {
        Delivery delivery = deliveryRepository
                .findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Delivery not found"));
        delivery.setCashCollected(amount);
        return deliveryRepository.save(delivery);
    }

    @Data
    public static class AssignDeliveryRequest {
        private UUID billId;
        private UUID deliveryBoyId;
        private DeliveryType type;
        private LocalDate scheduledDate;
    }
}