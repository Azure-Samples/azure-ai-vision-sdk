package com.azure.ai.vision.face.deviceattestation.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntegrityChecksTest {

    @Test
    void acceptsFreshTimestamp() {
        var verdict = new PlayIntegrityVerdict();
        verdict.requestDetails = new PlayIntegrityVerdict.RequestDetails();
        verdict.requestDetails.timestampMillis = Long.toString(System.currentTimeMillis());

        assertTrue(IntegrityChecks.verifyTimestamp(verdict).ok());
    }

    @Test
    void rejectsMissingOrInvalidTimestamp() {
        var invalidVerdict = new PlayIntegrityVerdict();
        invalidVerdict.requestDetails = new PlayIntegrityVerdict.RequestDetails();
        invalidVerdict.requestDetails.timestampMillis = "not-a-timestamp";

        assertEquals("INTEGRITY_TIMESTAMP_INVALID",
                IntegrityChecks.verifyTimestamp(new PlayIntegrityVerdict()).reason());
        assertEquals("INTEGRITY_TIMESTAMP_INVALID",
                IntegrityChecks.verifyTimestamp(invalidVerdict).reason());
    }
}