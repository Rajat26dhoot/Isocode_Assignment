package com.companya.customs.repository;
import com.companya.customs.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CrossCheckDiscrepancyRepository extends JpaRepository<CrossCheckDiscrepancy, Long> { List<CrossCheckDiscrepancy> findByShipmentId(Long shipmentId); void deleteByShipmentIdAndRuleCode(Long shipmentId, Enums.RuleCode ruleCode); boolean existsByShipmentIdAndStatus(Long shipmentId, Enums.DiscrepancyStatus status); }
