package com.eventreceiver.service;

import com.eventreceiver.model.Event;
import java.util.List;

public interface S3Service {
    void storeEvents(List<Event> events, String customerTier);
} 