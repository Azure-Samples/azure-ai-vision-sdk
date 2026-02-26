// Face Liveness SDK JavaScript interop for Blazor
// CRITICAL: The Face SDK runs inside an IFRAME (face-detector.html) to isolate its
// Emscripten WASM runtime from Blazor's .NET WASM runtime. Both runtimes define
// global Emscripten symbols (e.g. addRunDependency) which collide if loaded in the
// same page. The iframe gives the SDK its own global scope.

let activeDotNetHelper = null;

/**
 * Start liveness detection inside an isolated iframe.
 * @param {string} containerId - The ID of the container div that will hold the iframe
 * @param {string} authToken - The session authorization token
 * @param {object} dotNetHelper - DotNetObjectReference for callbacks
 */
export async function startLivenessDetection(containerId, authToken, dotNetHelper) {
    const container = document.getElementById(containerId);
    if (!container) {
        console.error("[FaceLiveness] Container not found:", containerId);
        await dotNetHelper.invokeMethodAsync("OnLivenessError", JSON.stringify({ livenessError: "Container element not found" }));
        return;
    }

    activeDotNetHelper = dotNetHelper;

    // Clean up any existing iframe
    const existing = container.querySelector("iframe");
    if (existing) {
        container.removeChild(existing);
    }

    // Create the iframe that will host the Face SDK in an isolated scope
    const iframe = document.createElement("iframe");
    iframe.src = "/face-detector.html";
    iframe.style.width = "100%";
    iframe.style.height = "100vh";
    iframe.style.border = "none";
    iframe.allow = "camera";
    container.appendChild(iframe);

    // Listen for messages from the iframe
    function onMessage(event) {
        if (!event.data?.type) return;

        switch (event.data.type) {
            case "liveness-ready":
                // iframe is loaded and ready — send the auth token to start detection
                console.log("[FaceLiveness] Iframe ready, sending start command");
                iframe.contentWindow.postMessage({ type: "start-liveness", authToken }, "*");
                break;

            case "liveness-success":
                console.log("[FaceLiveness] Detection succeeded");
                window.removeEventListener("message", onMessage);
                if (activeDotNetHelper) {
                    activeDotNetHelper.invokeMethodAsync("OnLivenessSuccess", event.data.result);
                }
                break;

            case "liveness-error":
                console.log("[FaceLiveness] Detection error:", event.data.error);
                window.removeEventListener("message", onMessage);
                if (activeDotNetHelper) {
                    activeDotNetHelper.invokeMethodAsync("OnLivenessError", JSON.stringify(event.data.error));
                }
                break;
        }
    }

    window.addEventListener("message", onMessage);
}

/**
 * Remove the face liveness detector iframe from the container.
 * @param {string} containerId - The ID of the container div
 */
export function destroyLivenessDetector(containerId) {
    const container = document.getElementById(containerId);
    if (container) {
        const iframe = container.querySelector("iframe");
        if (iframe) {
            container.removeChild(iframe);
            console.log("[FaceLiveness] Detector iframe removed");
        }
    }
    activeDotNetHelper = null;
}

/**
 * Generate a dummy device ID from available media devices.
 * Replicates logic from samples/web/nextjs/face/utils.ts
 */
export async function getDummyDeviceId() {
    let deviceId;
    try {
        const devices = await navigator.mediaDevices.enumerateDevices();
        const device = devices.find(
            (d) => d.deviceId !== "" && (/^[a-f0-9]+$/i.test(d.deviceId) || d.deviceId.endsWith("="))
        );
        deviceId = device?.deviceId;
    } catch {
        deviceId = undefined;
    }

    if (deviceId) {
        deviceId = deviceId.endsWith("=")
            ? Array.from(atob(deviceId), (char) =>
                ("0" + char.charCodeAt(0).toString(16)).slice(-2)
            ).join("")
            : deviceId;
    } else {
        deviceId = globalThis.crypto?.randomUUID()?.replace(/-/g, "") || "0".repeat(64);
    }

    deviceId = "0".repeat(64 - deviceId.length) + deviceId;
    deviceId = (
        BigInt("0x" + deviceId.substring(0, 32)) ^
        BigInt("0x" + deviceId.substring(32, 64))
    )
        .toString(16)
        .substring(0, 32);
    deviceId =
        ("0".repeat(32 - deviceId.length) + deviceId)
            .match(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/)
            ?.slice(1)
            .join("-") || "";

    return deviceId;
}

/**
 * Generate a dummy user ID.
 * Replicates logic from samples/web/nextjs/face/utils.ts
 */
export async function getDummyUserId() {
    let userId = globalThis.crypto?.randomUUID()?.replace(/-/g, "") || "0".repeat(64);

    userId = "0".repeat(64 - userId.length) + userId;
    userId = (
        BigInt("0x" + userId.substring(0, 32)) ^
        BigInt("0x" + userId.substring(32, 64))
    )
        .toString(16)
        .substring(0, 32);
    userId =
        ("0".repeat(32 - userId.length) + userId)
            .match(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/)
            ?.slice(1)
            .join("-") || "";

    return userId;
}
