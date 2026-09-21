package com.thinklab.domain.exception;

import com.thinklab.application.mapper.MaintenanceTicketMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessExceptionGuardsTest {

    private static class TestException extends BusinessException {
        TestException(String code, String message) {
            super(code, message);
        }

        TestException(String code, String message, Throwable cause) {
            super(code, message, cause);
        }
    }

    @Test
    @DisplayName("BusinessException validates code, message and cause")
    void guards() {
        Throwable cause = new IllegalStateException("root");
        TestException withCause = new TestException("ERR-X", "boom", cause);
        assertEquals("ERR-X", withCause.getErrorCode());
        assertEquals(cause, withCause.getCause());
        assertThrows(NullPointerException.class, () -> new TestException("ERR-X", "boom", null));
        assertThrows(IllegalArgumentException.class, () -> new TestException(" ", "boom"));
        assertThrows(IllegalArgumentException.class, () -> new TestException("ERR-X", " "));
        assertThrows(NullPointerException.class, () -> new TestException(null, "boom"));
        assertThrows(NullPointerException.class, () -> new TestException("ERR-X", null));
    }

    @Test
    @DisplayName("the ticket not-found exception accepts a free-form message")
    void notFoundMessage() {
        assertEquals("gone", new MaintenanceTicketNotFoundException("gone").getMessage());
    }

    @Test
    @DisplayName("the ticket mapper is a non-instantiable utility")
    void mapperUtility() throws Exception {
        Constructor<MaintenanceTicketMapper> constructor = MaintenanceTicketMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, thrown.getCause());
    }
}
