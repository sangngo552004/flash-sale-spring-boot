package com.sangngo552004.flashsale.util;

public class Const {
    public static final String OUTBOX_STATUS_PENDING = "PENDING";
    public static final String OUTBOX_STATUS_SENT = "SENT";
    public static final String OUTBOX_STATUS_FAILED = "FAILED";

    public static final String REDIS_KEY_PRODUCT_STOCK = "product_stock:";
    public static final String REDIS_KEY_RATE_LIMIT = "rate_limit:purchase:";
}
