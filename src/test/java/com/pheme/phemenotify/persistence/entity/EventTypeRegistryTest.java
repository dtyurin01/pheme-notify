package com.pheme.phemenotify.persistence.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventTypeRegistryTest {

    @Mock
    EventType mockEventType;

    @Test
    void shouldReturnEventType_whenCodeIsValid(){
        when(mockEventType.getCode()).thenReturn("ORDER_COMPLETED");
        EventTypeRegistry registry = new EventTypeRegistry(List.of(mockEventType));

        assertThat(registry.getByCode("ORDER_COMPLETED")).isEqualTo(mockEventType);
    }

    @Test
    void shouldThrowException_whenCodeIsUnknown(){
        when(mockEventType.getCode()).thenReturn("ORDER_COMPLETED");
        EventTypeRegistry registry = new EventTypeRegistry(List.of(mockEventType));

        assertThatThrownBy(() -> registry.getByCode("UNKNOWN_CODE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown EventType code: UNKNOWN_CODE");
    }

    @Test
    void shouldThrowException_whenDuplicateCodeOnStartup(){
        EventType duplicate = mock(EventType.class);

        String duplicateCode = "DUPLICATE_CODE";
        when(mockEventType.getCode()).thenReturn(duplicateCode);
        when(duplicate.getCode()).thenReturn(duplicateCode);

        List<EventType> typesWithDuplicates = List.of(mockEventType, duplicate);

        assertThatThrownBy(() -> new EventTypeRegistry(typesWithDuplicates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate EventType code detected");
    }
}
