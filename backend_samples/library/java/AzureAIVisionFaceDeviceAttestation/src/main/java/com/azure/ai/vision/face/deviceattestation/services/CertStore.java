package com.azure.ai.vision.face.deviceattestation.services;

import java.util.HashMap;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.IsoTime;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.crypto.CertUtils;
import com.azure.ai.vision.face.deviceattestation.store.CertificateData;
import com.azure.ai.vision.face.deviceattestation.store.Snapshot;
import com.azure.ai.vision.face.deviceattestation.store.UpdateResult;

/**
 * Certificate store, keyed by the SHA-256 thumbprint of the cert DER.
 * Persistence, key namespace, and TTL are delegated to the injected store; this
 * layer keeps the domain concerns (thumbprint computation, domain events,
 * metadata merge).
 */
public final class CertStore {

    /**
     * Save a certificate: create it on first sight, or bump its {@code lastVerifiedAt}
     * on subsequent calls. Returns the thumbprint + a new flag, or null on error.
     */
    public static SaveCertificateResult saveCertificate(
            AttestationContext ctx,
            String clientId,
            String system,
            String publicCert,
            Map<String, Object> metadata) {
        String thumbprint = CertUtils.computeCertThumbprint(publicCert);
        if (thumbprint == null) {
            ctx.logger.trackEvent("CertStore.SaveFail",
                    Maps.of("reason", "THUMBPRINT_COMPUTE_FAIL", "clientId", clientId, "system", system), null);
            return null;
        }

        Snapshot<CertificateData> existing = ctx.store.getCertificate(thumbprint);
        if (existing != null) {
            if (!clientId.equals(existing.value().clientId) || !system.equals(existing.value().system)) return null;
            existing.value().lastVerifiedAt = IsoTime.now();
            if (ctx.store.updateCertificate(thumbprint, existing.version(), existing.value()) != UpdateResult.APPLIED) {
                return null;
            }
            ctx.logger.trackEvent("CertStore.Updated",
                    Maps.of("thumbprint", thumbprint, "clientId", clientId, "system", system, "isNew", false), null);
            return new SaveCertificateResult(thumbprint, false);
        }

        String now = IsoTime.now();
        CertificateData certData = new CertificateData();
        certData.clientId = clientId;
        certData.system = system;
        certData.thumbprint = thumbprint;
        certData.publicCert = publicCert;
        certData.createdAt = now;
        certData.lastVerifiedAt = now;
        certData.metadata = metadata;
        if (!ctx.store.setCertificate(thumbprint, certData)) {
            return null;
        }
        ctx.logger.trackEvent("CertStore.Created",
                Maps.of("thumbprint", thumbprint, "clientId", clientId, "system", system, "isNew", true), null);
        return new SaveCertificateResult(thumbprint, true);
    }

    /** Load a certificate record by thumbprint. */
    public static Snapshot<CertificateData> getCertificate(AttestationContext ctx, String thumbprint) {
        return ctx.store.getCertificate(thumbprint);
    }

    /**
     * Merge fields into a cert record's metadata and bump {@code lastVerifiedAt},
     * preserving the existing TTL. Used to advance the assertion sign-count.
     * Returns false if the key is gone/expired.
     */
    public static boolean updateCertificateMetadata(
            AttestationContext ctx,
            String thumbprint,
            Map<String, Object> partialMetadata,
            Snapshot<CertificateData> snapshot) {
        CertificateData existing = snapshot.value();

        existing.lastVerifiedAt = IsoTime.now();
        Map<String, Object> merged = existing.metadata != null ? existing.metadata : new HashMap<>();
        merged.putAll(partialMetadata);
        existing.metadata = merged;

        return ctx.store.updateCertificate(thumbprint, snapshot.version(), existing) == UpdateResult.APPLIED;
    }

    private CertStore() {
    }
}
