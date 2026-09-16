package com.azure.ai.vision.face.sample.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable HTTP surface — all route paths and query-param names live here so
 * the library never hardcodes them. Bound from {@code app.routes.*}.
 */
@ConfigurationProperties(prefix = "app.routes")
public class ApiRoutes {

    private String challenge = "/api/attestation/challenge";
    private String register = "/api/attestation/register";
    private String verify = "/api/attestation/verify";
    private String sessionToken = "/api/session/token";
    private String livenessDigest = "/api/liveness/digest";
    private String sessionResult = "/api/session/result";
    private String healthz = "/healthz";
    private String appleAppSiteAssociation = "/.well-known/apple-app-site-association";
    private String assetLinks = "/.well-known/assetlinks.json";
    private String sessionIdParam = "s";
    private String clientIdParam = "cid";
    private String systemParam = "sys";

    public String getChallenge() { return challenge; }
    public void setChallenge(String v) { this.challenge = v; }
    public String getRegister() { return register; }
    public void setRegister(String v) { this.register = v; }
    public String getVerify() { return verify; }
    public void setVerify(String v) { this.verify = v; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String v) { this.sessionToken = v; }
    public String getLivenessDigest() { return livenessDigest; }
    public void setLivenessDigest(String v) { this.livenessDigest = v; }
    public String getSessionResult() { return sessionResult; }
    public void setSessionResult(String v) { this.sessionResult = v; }
    public String getHealthz() { return healthz; }
    public void setHealthz(String v) { this.healthz = v; }
    public String getAppleAppSiteAssociation() { return appleAppSiteAssociation; }
    public void setAppleAppSiteAssociation(String v) { this.appleAppSiteAssociation = v; }
    public String getAssetLinks() { return assetLinks; }
    public void setAssetLinks(String v) { this.assetLinks = v; }
    public String getSessionIdParam() { return sessionIdParam; }
    public void setSessionIdParam(String v) { this.sessionIdParam = v; }
    public String getClientIdParam() { return clientIdParam; }
    public void setClientIdParam(String v) { this.clientIdParam = v; }
    public String getSystemParam() { return systemParam; }
    public void setSystemParam(String v) { this.systemParam = v; }
}
