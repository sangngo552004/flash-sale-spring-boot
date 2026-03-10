package com.sangngo552004.flashsale.worker;

import org.springframework.stereotype.Component;

@Component
public class OutboxWorker {
    /*
     * SQL polling has been intentionally disabled.
     * Outbox events are now expected to be streamed from MySQL binlog by Debezium CDC.
     */
}
