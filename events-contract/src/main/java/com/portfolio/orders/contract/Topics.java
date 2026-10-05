package com.portfolio.orders.contract;

/** Default topic names shared by producers and consumers. Overridable via configuration. */
public final class Topics {

    public static final String ORDER_CREATED = "orders.order-created.v1";
    public static final String ORDER_CREATED_DLT = "orders.order-created.v1.dlt";

    private Topics() {
    }
}
