using NUnit.Framework;
using Microsoft.Playwright.NUnit;
using Microsoft.Playwright;

namespace PlaywrightTests;

[TestFixture]
public class FaceLivenessDetectorTests : PageTest
{
    // The app URL — override via environment variable for CI
    private string AppUrl => Environment.GetEnvironmentVariable("APP_URL") ?? "https://localhost:7165";

    public override BrowserNewContextOptions ContextOptions()
    {
        return new BrowserNewContextOptions
        {
            IgnoreHTTPSErrors = true,
            Permissions = new[] { "camera" }
        };
    }

    [Test]
    public async Task AppLoads_ShowsStartButtons()
    {
        await Page.GotoAsync(AppUrl);

        // Wait for Blazor to fully load
        await Page.WaitForSelectorAsync("text=Start Passive", new() { Timeout = 30000 });

        var passiveButton = Page.GetByText("Start Passive", new() { Exact = true });
        var passiveActiveButton = Page.GetByText("Start PassiveActive", new() { Exact = true });

        await Expect(passiveButton).ToBeVisibleAsync();
        await Expect(passiveActiveButton).ToBeVisibleAsync();
    }

    [Test]
    public async Task SdkModuleLoads_CustomElementRegistered()
    {
        await Page.GotoAsync(AppUrl);

        // Wait for Blazor and SDK to load
        await Page.WaitForSelectorAsync("text=Start Passive", new() { Timeout = 30000 });

        // Check that the custom element was registered by the SDK import
        var isRegistered = await Page.EvaluateAsync<bool>(
            "() => customElements.get('azure-ai-vision-face-ui') !== undefined");

        Assert.That(isRegistered, Is.True, "The azure-ai-vision-face-ui custom element should be registered");
    }

    [Test]
    public async Task PassiveActiveFlow_CameraPreviewAppears()
    {
        // Launch browser with fake camera
        await using var browser = await Playwright.Chromium.LaunchAsync(new BrowserTypeLaunchOptions
        {
            Args = new[]
            {
                "--use-fake-device-for-media-stream",
                "--use-fake-ui-for-media-stream"
            }
        });

        var context = await browser.NewContextAsync(new BrowserNewContextOptions
        {
            IgnoreHTTPSErrors = true,
            Permissions = new[] { "camera" }
        });

        var page = await context.NewPageAsync();
        await page.GotoAsync(AppUrl);

        // Wait for app to load
        await page.WaitForSelectorAsync("text=Start PassiveActive", new() { Timeout = 30000 });

        // Click Start PassiveActive
        await page.GetByText("Start PassiveActive", new() { Exact = true }).ClickAsync();

        // Wait for the face-container to appear and the SDK web component to be created
        await page.WaitForSelectorAsync("#face-container azure-ai-vision-face-ui", new() { Timeout = 30000 });

        // Verify a video element exists (SDK creates a video element for camera)
        // The video may be in the shadow DOM of the web component
        var hasVideo = await page.EvaluateAsync<bool>(@"() => {
            const detector = document.querySelector('azure-ai-vision-face-ui');
            if (!detector) return false;
            // Check in shadow DOM
            if (detector.shadowRoot) {
                const video = detector.shadowRoot.querySelector('video');
                if (video) return true;
            }
            // Check in regular DOM (fallback)
            const video = document.querySelector('video');
            return video !== null;
        }");

        Assert.That(hasVideo, Is.True, "A video element should exist after starting liveness detection");

        // Take a screenshot for visual verification
        await page.ScreenshotAsync(new PageScreenshotOptions
        {
            Path = "test-camera-preview.png"
        });
    }

    [Test]
    public async Task ApiEndpoint_GenerateAccessToken_Responds()
    {
        // Test the API endpoint by making a request via JS from the page context
        await Page.GotoAsync(AppUrl);
        await Page.WaitForSelectorAsync("text=Start Passive", new() { Timeout = 30000 });

        var status = await Page.EvaluateAsync<int>(@"async () => {
            const form = new FormData();
            form.append('Action', 'detectLiveness');
            form.append('parameters', JSON.stringify({
                livenessOperationMode: 'Passive',
                deviceCorrelationId: '00000000-0000-0000-0000-000000000000',
                userCorrelationId: '00000000-0000-0000-0000-000000000000'
            }));
            const res = await fetch('/api/generateAccessToken', { method: 'POST', body: form });
            return res.status;
        }");

        // Should get either 200 (success) or 400 (bad credentials in test env)
        // Both prove the endpoint is wired up correctly
        Assert.That(status, Is.AnyOf(200, 400),
            "API endpoint should respond (200 success or 400 validation error)");
    }
}
