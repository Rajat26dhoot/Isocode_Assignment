package com.companya.customs.service;

import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ShipmentAccessService {
    private final ShipmentRepository shipments;
    private final ShipmentDocumentRepository documents;
    private final ExtractedFieldRepository fields;
    private final LineItemRepository items;

    public Shipment requireShipment(Long id, User user) {
        Shipment shipment = shipments.findById(id).orElseThrow(() -> new ApiExceptions.NotFound("Shipment not found: " + id));
        if (user.getRole() == Enums.Role.ROLE_EXECUTIVE && (shipment.getAssignedExecutive() == null || !shipment.getAssignedExecutive().getId().equals(user.getId())))
            throw new ApiExceptions.NotFound("Shipment not found: " + id);
        return shipment;
    }
    public ShipmentDocument requireDocument(Long id, User user) {
        ShipmentDocument d = documents.findById(id).orElseThrow(() -> new ApiExceptions.NotFound("Document not found: " + id));
        requireShipment(d.getShipment().getId(), user); return d;
    }
    public ExtractedField requireField(Long id, User user) {
        ExtractedField f = fields.findById(id).orElseThrow(() -> new ApiExceptions.NotFound("Field not found: " + id));
        requireShipment(f.getDocument().getShipment().getId(), user); return f;
    }
    public LineItem requireLineItem(Long id, User user) {
        LineItem item = items.findById(id).orElseThrow(() -> new ApiExceptions.NotFound("Line item not found: " + id));
        requireShipment(item.getDocument().getShipment().getId(), user); return item;
    }
}
