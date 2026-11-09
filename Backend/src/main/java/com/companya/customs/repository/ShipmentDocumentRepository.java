package com.companya.customs.repository;
import com.companya.customs.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ShipmentDocumentRepository extends JpaRepository<ShipmentDocument, Long> { List<ShipmentDocument> findByShipmentId(Long shipmentId); Optional<ShipmentDocument> findFirstByShipmentIdAndDocTypeOrderByIdDesc(Long shipmentId, Enums.DocumentType docType); }
