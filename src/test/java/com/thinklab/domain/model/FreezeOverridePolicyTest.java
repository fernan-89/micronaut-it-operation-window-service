package com.thinklab.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreezeOverridePolicyTest {

    @Test
    @DisplayName("ADMIN and SERVICE may waive a freeze, and so may a caller with no role (security off); OPERATOR, REQUESTER and VIEWER may not")
    void permits() {
        assertTrue(FreezeOverridePolicy.permits("ADMIN"));
        assertTrue(FreezeOverridePolicy.permits("SERVICE"));
        assertTrue(FreezeOverridePolicy.permits(null));
        assertFalse(FreezeOverridePolicy.permits("OPERATOR"));
        assertFalse(FreezeOverridePolicy.permits("REQUESTER"));
        assertFalse(FreezeOverridePolicy.permits("VIEWER"));
        assertFalse(FreezeOverridePolicy.permits("something-else"));
    }

    @Test
    @DisplayName("the policy is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<FreezeOverridePolicy> constructor = FreezeOverridePolicy.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }
}
