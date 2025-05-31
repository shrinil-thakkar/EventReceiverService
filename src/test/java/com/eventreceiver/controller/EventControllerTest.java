package com.eventreceiver.controller;

import com.eventreceiver.config.AppConfig;
import com.eventreceiver.model.Event;
import com.eventreceiver.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventControllerTest {
    private MockMvc mockMvc;
    private EventService eventService;
    private AppConfig appConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        appConfig = mock(AppConfig.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        mockMvc = MockMvcBuilders
            .standaloneSetup(new EventController(appConfig, eventService, meterRegistry))
            .build();
    }

    @Test
    void ingestEvent_ValidRequest_ReturnsSuccess() throws Exception {
        // Given
        Event event = new Event();
        event.setEventTimestamp(Instant.now());
        event.setBody("test body");
        String customerTier = "premium";
        
        when(appConfig.getAllowedCustomerTiers()).thenReturn(List.of("premium"));
        doNothing().when(eventService).processEvent(any(Event.class), eq(customerTier));

        // When/Then
        mockMvc.perform(post("/api/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Tier", customerTier)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("success"));

        verify(eventService).processEvent(any(Event.class), eq(customerTier));
    }

    @Test
    void ingestEvent_InvalidCustomerTier_ReturnsBadRequest() throws Exception {
        // Given
        Event event = new Event();
        event.setEventTimestamp(Instant.now());
        event.setBody("test body");
        String customerTier = "invalid";
        
        when(appConfig.getAllowedCustomerTiers()).thenReturn(List.of("premium"));

        // When/Then
        mockMvc.perform(post("/api/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Tier", customerTier)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unauthorized customer tier"));

        verify(eventService, never()).processEvent(any(), any());
    }

    @Test
    void ingestEvent_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        // Given
        Event event = new Event();
        event.setEventTimestamp(Instant.now());
        event.setBody("test body");
        String customerTier = "premium";
        
        when(appConfig.getAllowedCustomerTiers()).thenReturn(List.of("premium"));
        doThrow(new RuntimeException("Test error")).when(eventService).processEvent(any(), any());

        // When/Then
        mockMvc.perform(post("/api/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Customer-Tier", customerTier)
                .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Test error"));
    }
}
