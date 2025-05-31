package com.eventreceiver.service;

import com.eventreceiver.model.Event;

public interface EventService {
    void processEvent(Event event, String customerTier);
} 