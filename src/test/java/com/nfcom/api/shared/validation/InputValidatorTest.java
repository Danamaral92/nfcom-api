package com.nfcom.api.shared.validation;

import com.nfcom.api.shared.error.NfcomException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {

    // --- validateAccessKey ---

    @Test
    void validAccessKeyPasses() {
        assertDoesNotThrow(() -> InputValidator.validateAccessKey("35200600012345000176550000000012345678901234"));
    }

    @Test
    void accessKeyWithLessThan44DigitsThrows() {
        NfcomException ex = assertThrows(NfcomException.class,
                () -> InputValidator.validateAccessKey("1234567890123456789012345678901234567890123"));
        assertEquals(422, ex.getHttpStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void accessKeyWithMoreThan44DigitsThrows() {
        NfcomException ex = assertThrows(NfcomException.class,
                () -> InputValidator.validateAccessKey("123456789012345678901234567890123456789012345"));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void accessKeyWithNonDigitCharactersThrows() {
        NfcomException ex = assertThrows(NfcomException.class,
                () -> InputValidator.validateAccessKey("3520060001234500017655000000001234567890abcd"));
        assertEquals(422, ex.getHttpStatus());
        assertTrue(ex.getMessage().toLowerCase().contains("digit"));
    }

    @Test
    void nullAccessKeyThrows() {
        assertThrows(NfcomException.class, () -> InputValidator.validateAccessKey(null));
    }

    @Test
    void emptyAccessKeyThrows() {
        assertThrows(NfcomException.class, () -> InputValidator.validateAccessKey(""));
    }

    // --- validateCnpj ---

    @Test
    void validCnpjPasses() {
        assertDoesNotThrow(() -> InputValidator.validateCnpj("00000000000191"));
    }

    @Test
    void cnpjWithLessThan14DigitsThrows() {
        NfcomException ex = assertThrows(NfcomException.class,
                () -> InputValidator.validateCnpj("1234567890123"));
        assertEquals(422, ex.getHttpStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void cnpjWithMoreThan14DigitsThrows() {
        assertThrows(NfcomException.class,
                () -> InputValidator.validateCnpj("123456789012345"));
    }

    @Test
    void cnpjWithNonDigitCharactersThrows() {
        assertThrows(NfcomException.class,
                () -> InputValidator.validateCnpj("000000000001ab"));
    }

    @Test
    void nullCnpjThrows() {
        assertThrows(NfcomException.class, () -> InputValidator.validateCnpj(null));
    }

    @Test
    void emptyCnpjThrows() {
        assertThrows(NfcomException.class, () -> InputValidator.validateCnpj(""));
    }

    // --- validateEventType ---

    @Test
    void validEventTypesPass() {
        assertDoesNotThrow(() -> InputValidator.validateEventType("110111"));
        assertDoesNotThrow(() -> InputValidator.validateEventType("240140"));
        assertDoesNotThrow(() -> InputValidator.validateEventType("240150"));
        assertDoesNotThrow(() -> InputValidator.validateEventType("240151"));
        assertDoesNotThrow(() -> InputValidator.validateEventType("240160"));
        assertDoesNotThrow(() -> InputValidator.validateEventType("240161"));
        assertDoesNotThrow(() -> InputValidator.validateEventType("240162"));
        assertDoesNotThrow(() -> InputValidator.validateEventType("240170"));
    }

    @Test
    void invalidEventTypeThrows() {
        NfcomException ex = assertThrows(NfcomException.class,
                () -> InputValidator.validateEventType("999999"));
        assertEquals(422, ex.getHttpStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void nullEventTypeThrows() {
        assertThrows(NfcomException.class, () -> InputValidator.validateEventType((String) null));
    }
}
