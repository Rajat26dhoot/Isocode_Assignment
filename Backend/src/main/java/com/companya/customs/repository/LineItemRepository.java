package com.companya.customs.repository;
import com.companya.customs.entity.LineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LineItemRepository extends JpaRepository<LineItem, Long> { List<LineItem> findByDocumentIdOrderByItemNumber(Long documentId); List<LineItem> findByDocumentShipmentId(Long shipmentId); }
