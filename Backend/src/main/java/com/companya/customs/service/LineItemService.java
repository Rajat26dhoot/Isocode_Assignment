package com.companya.customs.service;

import com.companya.customs.dto.ShipmentDtos.LineItemCreateRequest;
import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;

@Service @RequiredArgsConstructor
public class LineItemService {
    private final ShipmentAccessService access;
    private final LineItemRepository items;
    private final AuditService audit;
    private final CrossCheckService crossChecks;

    @Transactional public LineItem create(Long documentId, LineItemCreateRequest request, User user) {
        ShipmentDocument document = access.requireDocument(documentId, user);
        if (document.getDocType() != Enums.DocumentType.INVOICE && document.getDocType() != Enums.DocumentType.PACKING_LIST) throw new ApiExceptions.Validation("Line items can only be added to an invoice or packing list");
        ShipmentStatusGuard.ensureEditable(document.getShipment());
        boolean invoice = document.getDocType() == Enums.DocumentType.INVOICE;
        if (invoice && (request.unitPrice() == null || request.lineTotal() == null || request.currency() == null || request.currency().isBlank())) throw new ApiExceptions.Validation("Invoice line items require unit price, currency, and line total");
        String currency = request.currency() == null ? null : request.currency().trim().toUpperCase(java.util.Locale.ROOT);
        if (invoice && !currency.matches("[A-Z]{3}")) throw new ApiExceptions.Validation("Currency must be a three-letter currency code");
        int next = items.findByDocumentIdOrderByItemNumber(documentId).stream().map(LineItem::getItemNumber).filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(0) + 1;
        LineItem item = items.save(LineItem.builder().document(document).itemNumber(next).description(request.description().trim()).hsnCode(request.hsnCode() == null ? "" : request.hsnCode().trim()).quantity(request.quantity()).unitPrice(request.unitPrice()).currency(currency).lineTotal(request.lineTotal()).confidenceScore(java.math.BigDecimal.ZERO).isHumanVerified(true).build());
        audit.logAction(document.getShipment(), document.getId(), (invoice ? "Invoice" : "Packing list") + " line item " + next, "LINE_ITEM_CREATED", null, item.getDescription(), user, "Manual line item entry");
        crossChecks.runForShipment(document.getShipment().getId());
        return item;
    }

    private static final class ShipmentStatusGuard {
        static void ensureEditable(Shipment shipment) {
            if (shipment.getStatus() == Enums.ShipmentStatus.PENDING_APPROVAL || shipment.getStatus() == Enums.ShipmentStatus.APPROVED || shipment.getStatus() == Enums.ShipmentStatus.EXPORTED)
                throw new ApiExceptions.Validation("This shipment cannot be edited in its current status");
        }
    }
}
