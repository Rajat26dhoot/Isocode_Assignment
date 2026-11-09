package com.companya.customs.repository;
import com.companya.customs.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
 Optional<Shipment> findByShipmentCode(String shipmentCode);
 java.util.List<Shipment> findByAssignedExecutiveId(Long userId);
}
