package com.azure.ai.vision.face.deviceattestation.store;

/**
 * Cluster-wide persistent-storage abstraction for the attestation flow. Two
 * entry kinds are stored: liveness <b>sessions</b> (keyed by session UUID) and
 * attestation <b>certificates</b> (keyed by SHA-256 thumbprint).
 * <p>
 * The store owns ALL time-to-live policy: {@code set*} creates an absent
 * entry with the store's configured default TTL (session vs certificate);
 * {@code update*} rewrites an existing entry while PRESERVING its remaining TTL
 * conditional on its snapshot revision. Reads return detached values; every
 * successful write assigns a unique revision, including recreation after expiry.
 * Failures throw StorageException, never masquerade as missing records.
 * <p>
 * The concrete implementation is provided by the host (see the Spring Boot
 * sample's Redis store) so a consumer can back the store with Redis, SQL, etc.
 * Methods are synchronous; a blocking servlet host calls them directly.
 */
public interface ClusterStore {

    /** Read a session record by UUID, or null if absent/expired. */
    Snapshot<SessionRecord> getSession(String sid);

    /** Create an absent session with a fresh TTL; false means already exists. */
    boolean setSession(String sid, SessionRecord record);

    /**
    * Atomically replace a matching session revision, preserving expiry.
     */
    UpdateResult updateSession(String sid, String expectedVersion, SessionRecord record);

    /** Read a certificate record by thumbprint, or null if absent/expired. */
    Snapshot<CertificateData> getCertificate(String thumbprint);

    /** Create an absent certificate with a fresh TTL; false means already exists. */
    boolean setCertificate(String thumbprint, CertificateData data);

    /**
    * Atomically replace a matching certificate revision, preserving expiry.
     */
    UpdateResult updateCertificate(String thumbprint, String expectedVersion, CertificateData data);
}
