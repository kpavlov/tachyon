/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.langchain4j;

import com.example.langchain4j.model.OrderConfirmation;
import com.example.langchain4j.model.OrderItem;
import com.example.langchain4j.model.PlaceOrderRequest;
import dev.langchain4j.agent.tool.Tool;
import java.util.Locale;

/**
 * Plain LangChain4j {@code @Tool} annotated service — no Tachyon imports. {@link
 * dev.tachyonmcp.annotations.langchain4j.LangChain4jAnnotationProvider} scans {@link #placeOrder}
 * and, because it takes and returns a composite (record) type, describes and binds it via
 * LangChain4j's own {@code ToolSpecifications} schema generator rather than Tachyon's scalar-only
 * mapping.
 */
public class OrderService {

    private static final double UNIT_PRICE = 9.99;

    @Tool("Places an order for a customer and returns a confirmation with computed totals")
    public OrderConfirmation placeOrder(PlaceOrderRequest request) {
        int totalItems = request.items().stream().mapToInt(OrderItem::quantity).sum();
        double totalPrice = Math.round(totalItems * UNIT_PRICE * 100) / 100.0;
        String orderId = "ORD-%s-%d".formatted(request.customer().toUpperCase(Locale.ROOT), totalItems);
        return new OrderConfirmation(orderId, request.customer(), totalItems, totalPrice);
    }
}
