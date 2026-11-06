package com.companya.customs.repository;
import com.companya.customs.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, Long> { List<ExtractedField> findByDocumentId(Long documentId); List<ExtractedField> findByDocumentShipmentId(Long shipmentId); Optional<ExtractedField> findByDocumentIdAndFieldKey(Long documentId, Enums.FieldKey fieldKey); }
