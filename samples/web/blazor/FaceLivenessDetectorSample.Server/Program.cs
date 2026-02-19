var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddHttpClient();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseWebAssemblyDebugging();
}

// COOP/COEP headers — required for SharedArrayBuffer which the Face SDK's WASM needs.
// Must be placed BEFORE UseStaticFiles so all responses (including static assets) get
// these headers. Without this, the SDK's internal fetch of .wasm files may be blocked.
// The splash.html iframe uses the "credentialless" attribute to opt out of COEP enforcement,
// allowing its cross-origin Fluent UI script from unpkg.com to load.
app.Use(async (context, next) =>
{
    context.Response.Headers["Cross-Origin-Embedder-Policy"] = "require-corp";
    context.Response.Headers["Cross-Origin-Opener-Policy"] = "same-origin";
    await next();
});

// Serve Blazor WASM framework files
app.UseBlazorFrameworkFiles();

// Static files with WASM MIME type support
var provider = new Microsoft.AspNetCore.StaticFiles.FileExtensionContentTypeProvider();
provider.Mappings[".wasm"] = "application/wasm";
app.UseStaticFiles(new StaticFileOptions
{
    ContentTypeProvider = provider
});

app.UseRouting();
app.MapControllers();
app.MapFallbackToFile("index.html");

app.Run();

// Make Program accessible for Playwright test WebApplicationFactory
public partial class Program { }
