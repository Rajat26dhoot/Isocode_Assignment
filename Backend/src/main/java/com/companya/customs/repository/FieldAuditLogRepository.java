package com.companya.customs.repository;
import com.companya.customs.entity.FieldAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FieldAuditLogRepository extends JpaRepository<FieldAuditLog, Long> { List<FieldAuditLog> findByShipmentIdOrderByChangedAtDesc(Long shipmentId); }
