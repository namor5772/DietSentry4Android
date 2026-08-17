// main.mm — NSApplication + Metal + ImGui bootstrap, navigation host.
// macOS counterpart of winport/src/main.cpp (WinMain + D3D11). The per-frame
// body (root window, nav stack, toasts, deferred nav ops) matches it 1:1.
#include "app.h"
#include "ui.h"
#include "imgui_internal.h"   // ImTextCharFromUtf8 (Ctrl+V paste shim)
#include "imgui_impl_metal.h"
#include "imgui_impl_osx.h"
#import <Cocoa/Cocoa.h>
#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>

static App g_app;
static id<MTLDevice> g_device = nil;
static id<MTLCommandQueue> g_commandQueue = nil;
static MTKView* g_view = nil;
static bool g_menuPasteRequested = false;   // Edit -> Paste clicked (main thread)

// Pauses the MTKView render loop while a modal NSOpenPanel runs its own run
// loop (imageutil.mm), so a new frame can't start while one is mid-build.
void macSetRenderPaused(bool paused) {
    if (g_view) g_view.paused = paused;
}

// ---------------------------------------------------------------------------
// App navigation
// ---------------------------------------------------------------------------
void App::push(Screen* s) {
    pendingNavOps.push_back([this, s]() {
        nav.emplace_back(s);
    });
}

void App::pop() {
    pendingNavOps.push_back([this]() {
        if (nav.size() > 1) nav.pop_back();
    });
}

bool App::popTo(const char* route) {
    std::string r = route;
    pendingNavOps.push_back([this, r]() {
        while (nav.size() > 1 && r != nav.back()->route()) nav.pop_back();
    });
    return findScreen(route) != nullptr;
}

void App::requestBack() {
    if (nav.empty()) return;
    Screen* top = nav.back().get();
    if (!top->onBack(*this)) pop();
}

Screen* App::findScreen(const char* route) {
    for (auto& s : nav)
        if (strcmp(s->route(), route) == 0) return s.get();
    return nullptr;
}

void* App::createTextureRGBA(const unsigned char* rgba, int w, int h) {
    if (!g_device || w <= 0 || h <= 0) return nullptr;
    MTLTextureDescriptor* td =
        [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm
                                                           width:(NSUInteger)w
                                                          height:(NSUInteger)h
                                                       mipmapped:NO];
    td.usage = MTLTextureUsageShaderRead;
    td.storageMode = MTLStorageModeManaged;
    id<MTLTexture> tex = [g_device newTextureWithDescriptor:td];
    if (!tex) return nullptr;
    [tex replaceRegion:MTLRegionMake2D(0, 0, (NSUInteger)w, (NSUInteger)h)
           mipmapLevel:0
             withBytes:rgba
           bytesPerRow:(NSUInteger)w * 4];
    return (void*)CFBridgingRetain(tex);   // retained; released in releaseTexture
}

void App::releaseTexture(void* tex) {
    if (tex) CFRelease((CFTypeRef)tex);
}

// ---------------------------------------------------------------------------
// Startup helpers
// ---------------------------------------------------------------------------
static void loadPrompts(App& app) {
    std::wstring assets = assetsDirectory() + L"/";
    app.nipPrompt = readTextFile(assets + L"NIPsysprompt.txt")
        .value_or("You are a helpful assistant for the Diet Sentry food tracking app.");
    app.recipePrompt = readTextFile(assets + L"RECIPEsysprompt.txt").value_or("");
    app.explainPrompt = readTextFile(assets + L"EXPLAINsysprompt.txt")
        .value_or("You are a friendly nutrition assistant for the Diet Sentry food tracking app. "
                  "Briefly explain the user's daily food totals against Australian NHMRC NRVs in 2-3 plain-language paragraphs.");
    app.genericPrompt = trim(readTextFile(assets + L"GenericSysprompt.txt")
        .value_or("You are a helpful assistant for the Diet Sentry food tracking app."));
    app.genericWebSearchClause = trim(readTextFile(assets + L"GenericSysprompt_websearch.txt").value_or(""));
}

// First existing candidate is loaded; optional merge files (symbols) appended in order.
// A glyph missing from the base font is taken from the first merge font that has it,
// so list the preferred symbol font first and broad-coverage fallbacks after it.
static ImFont* loadFontOrDefault(ImGuiIO& io, std::initializer_list<const char*> candidates,
                                 std::initializer_list<const char*> mergePaths = {}) {
    ImFont* f = nullptr;
    for (const char* path : candidates) {
        if (fileExists(utf8ToWide(path))) {
            f = io.Fonts->AddFontFromFileTTF(path);
            if (f) break;
        }
    }
    if (f) {
        for (const char* mergePath : mergePaths) {
            if (!fileExists(utf8ToWide(mergePath))) continue;
            ImFontConfig cfg;
            cfg.MergeMode = true;
            io.Fonts->AddFontFromFileTTF(mergePath, 0.0f, &cfg);
        }
    }
    if (!f) f = io.Fonts->AddFontDefault();
    return f;
}

static void autoNavigate(App& app) {
    // Test/deep-link hook: DIETSENTRY_AUTONAV=<route> opens a screen at launch.
    const char* routeEnv = getenv("DIETSENTRY_AUTONAV");
    if (!routeEnv || !routeEnv[0]) return;
    std::string r = routeEnv;
    auto firstFoodId = [&]() {
        auto foods = app.db.readFoodsFromDatabase();
        return foods.empty() ? 0 : foods.front().foodId;
    };
    if (r == "eatenLog") app.nav.emplace_back(makeEatenLogScreen(app));
    else if (r == "utilities") app.nav.emplace_back(makeUtilitiesScreen(app));
    else if (r == "eatenGraph") { app.nav.emplace_back(makeUtilitiesScreen(app)); app.nav.emplace_back(makeEatenGraphScreen(app)); }
    else if (r == "addFoodByJson") app.nav.emplace_back(makeAddFoodByJsonScreen(app));
    else if (r == "addFoodByAi") app.nav.emplace_back(makeAddFoodByAiScreen(app));
    else if (r == "addRecipe") app.nav.emplace_back(makeAddRecipeScreen(app));
    else if (r == "insertFood") app.nav.emplace_back(makeInsertFoodScreen(app));
    else if (r == "editFirst") { int id = firstFoodId(); if (id) app.nav.emplace_back(makeEditFoodScreen(app, id)); }
}

// ---------------------------------------------------------------------------
// Renderer (MTKViewDelegate) — one ImGui frame per drawInMTKView callback
// ---------------------------------------------------------------------------
@interface DSRenderer : NSObject <MTKViewDelegate>
@end

@implementation DSRenderer

- (void)mtkView:(MTKView*)view drawableSizeWillChange:(CGSize)size {
    (void)view; (void)size;   // handled by the OSX backend via view bounds
}

- (void)drawInMTKView:(MTKView*)view {
    MTLRenderPassDescriptor* rpd = view.currentRenderPassDescriptor;
    if (rpd == nil) return;

    ImGui_ImplMetal_NewFrame(rpd);
    ImGui_ImplOSX_NewFrame(view);
    ImGui::NewFrame();

    // Paste helpers for remote-desktop use (RealVNC from a Windows keyboard
    // never delivers a usable Cmd+V). Two triggers feed the same splice:
    //  - Ctrl+V chord, when the VNC server exposes the Control modifier
    //    (imgui_impl_osx.mm carries a patch syncing modifiers off key events);
    //  - the Edit -> Paste menu item, which needs no keyboard at all. The
    //    macOS menu bar sits outside the ImGui view, so the focused field
    //    keeps its caret while the user mouses up to the menu.
    // The clipboard is spliced directly into this frame's character queue,
    // with Ctrl lifted for the frame so InputText's ignore_char_inputs guard
    // doesn't drop it. Native Cmd+V keeps working through ImGui's normal path.
    {
        ImGuiIO& io = ImGui::GetIO();
        bool chordPaste = io.WantTextInput && io.KeyCtrl && !io.KeySuper && !io.KeyAlt &&
                          ImGui::IsKeyPressed(ImGuiKey_V, false);
        if (g_menuPasteRequested && !io.WantTextInput)
            g_app.toast("Click into a text field first, then use Edit > Paste");
        if (chordPaste || (g_menuPasteRequested && io.WantTextInput)) {
            if (const char* clip = ImGui::GetClipboardText()) {
                io.KeyCtrl = false;
                io.KeyMods &= ~ImGuiMod_Ctrl;
                for (const char* p = clip; *p;) {
                    unsigned int cp = 0;
                    p += ImTextCharFromUtf8(&cp, p, nullptr);
                    if (cp == 0) break;
                    if (cp == '\r') continue;
                    if (cp > 0xFFFF && sizeof(ImWchar) == 2) {
                        io.InputQueueCharacters.push_back((ImWchar)(0xD800 + ((cp - 0x10000) >> 10)));
                        io.InputQueueCharacters.push_back((ImWchar)(0xDC00 + ((cp - 0x10000) & 0x3FF)));
                    } else {
                        io.InputQueueCharacters.push_back((ImWchar)cp);
                    }
                }
            }
        }
        g_menuPasteRequested = false;
    }

    // Root window fills the viewport; the active screen draws into it.
    ImGuiViewport* vp = ImGui::GetMainViewport();
    ImGui::SetNextWindowPos(vp->Pos);
    ImGui::SetNextWindowSize(vp->Size);
    ImGui::Begin("##root", nullptr,
                 ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
                 ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoBringToFrontOnFocus |
                 ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoScrollWithMouse |
                 ImGuiWindowFlags_NoSavedSettings);
    if (!g_app.nav.empty()) g_app.nav.back()->draw(g_app);
    g_app.endFrameKeyboardNav();
    ImGui::End();

    g_app.drawToasts();

    // Apply deferred navigation mutations
    for (auto& op : g_app.pendingNavOps) op();
    g_app.pendingNavOps.clear();

    ImGui::Render();
    id<MTLCommandBuffer> commandBuffer = [g_commandQueue commandBuffer];
    id<MTLRenderCommandEncoder> encoder = [commandBuffer renderCommandEncoderWithDescriptor:rpd];
    ImGui_ImplMetal_RenderDrawData(ImGui::GetDrawData(), commandBuffer, encoder);
    [encoder endEncoding];
    [commandBuffer presentDrawable:view.currentDrawable];
    [commandBuffer commit];

    if (g_app.quitRequested) [NSApp terminate:nil];
}

@end

// ---------------------------------------------------------------------------
// Window geometry persistence
//
// The window's content rect is remembered in prefs.json alongside every other
// setting, in AppKit screen points (origin bottom-left of the main display,
// y up). macOS-only for now: the Windows port still opens at its default
// footprint every time.
// ---------------------------------------------------------------------------
#define PREF_KEY_WINDOW_X "windowX"
#define PREF_KEY_WINDOW_Y "windowY"
#define PREF_KEY_WINDOW_W "windowW"
#define PREF_KEY_WINDOW_H "windowH"

static const int kGeomUnset = -1000000;   // sentinel: no geometry saved yet
static const CGFloat kMinContentW = 360;  // below this the two-column rows collapse
static const CGFloat kMinContentH = 420;

// Fits a remembered content rect to the displays that exist right now, since
// monitors get unplugged and resolutions change between runs. Returns NO when
// nothing sensible can be salvaged and the caller should use the default
// placement instead.
static BOOL fitSavedFrameToScreens(NSRect* io, NSWindowStyleMask mask) {
    NSRect content = *io;
    content.size.width  = std::max(content.size.width,  kMinContentW);
    content.size.height = std::max(content.size.height, kMinContentH);

    // Measure the real frame — title bar included — because that is the part
    // the user has to be able to see and grab.
    NSRect frameRect = [NSWindow frameRectForContentRect:content styleMask:mask];

    NSScreen* best = nil;
    CGFloat bestArea = 0;
    for (NSScreen* s in NSScreen.screens) {
        NSRect hit = NSIntersectionRect(frameRect, s.visibleFrame);
        CGFloat area = hit.size.width * hit.size.height;
        if (area > bestArea) { bestArea = area; best = s; }
    }
    if (!best) return NO;                       // lands on no current display

    NSRect vis = best.visibleFrame;
    NSRect hit = NSIntersectionRect(frameRect, vis);
    if (hit.size.width < 120 || hit.size.height < 40) return NO;   // untitled sliver

    // A size remembered from a larger display must not exceed today's.
    NSRect maxContent = [NSWindow contentRectForFrameRect:vis styleMask:mask];
    content.size.width  = std::min(content.size.width,  maxContent.size.width);
    content.size.height = std::min(content.size.height, maxContent.size.height);

    // Re-derive after any shrink and pull the window down if it now overhangs
    // the top — the one edge macOS will not let you drag back from.
    frameRect = [NSWindow frameRectForContentRect:content styleMask:mask];
    CGFloat overhang = NSMaxY(frameRect) - NSMaxY(vis);
    if (overhang > 0) content.origin.y -= overhang;

    *io = content;
    return YES;
}

// ---------------------------------------------------------------------------
// App delegate — window + view + ImGui/App init
// ---------------------------------------------------------------------------
@interface DSAppDelegate : NSObject <NSApplicationDelegate>
@property(strong) NSWindow* window;
@property(strong) DSRenderer* renderer;
@end

@implementation DSAppDelegate

- (void)pasteIntoActiveField:(id)sender {
    (void)sender;
    g_menuPasteRequested = true;   // consumed by the next ImGui frame
}

- (void)setupMainMenu {
    NSMenu* mainMenu = [[NSMenu alloc] init];
    NSMenuItem* appItem = [[NSMenuItem alloc] init];
    [mainMenu addItem:appItem];
    NSMenu* appMenu = [[NSMenu alloc] init];
    NSString* quitTitle = @"Quit DietSentry";
    NSMenuItem* quitItem = [[NSMenuItem alloc] initWithTitle:quitTitle
                                                      action:@selector(terminate:)
                                               keyEquivalent:@"q"];
    [appMenu addItem:quitItem];
    appItem.submenu = appMenu;

    // Edit menu with a mouse-clickable Paste — the reliable path when driving
    // the Mac over VNC, where Cmd/Ctrl chords may never arrive intact.
    NSMenuItem* editItem = [[NSMenuItem alloc] init];
    [mainMenu addItem:editItem];
    NSMenu* editMenu = [[NSMenu alloc] initWithTitle:@"Edit"];
    NSMenuItem* pasteItem = [[NSMenuItem alloc] initWithTitle:@"Paste"
                                                       action:@selector(pasteIntoActiveField:)
                                                keyEquivalent:@""];
    pasteItem.target = self;
    [editMenu addItem:pasteItem];
    editItem.submenu = editMenu;

    NSApp.mainMenu = mainMenu;
}

- (void)applicationDidFinishLaunching:(NSNotification*)notification {
    (void)notification;
    [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular];
    [self setupMainMenu];

    // Loaded before the window is built so it can open at the remembered size
    // straight away, rather than being created and then resized.
    g_app.prefs.load();

    // Default footprint, same as the Windows port: 540x1000 near the top-right
    // corner, clamped to 96% of the visible screen height. Used on first run and
    // whenever the remembered geometry no longer fits the current displays.
    NSScreen* screen = NSScreen.mainScreen;
    NSRect visible = screen ? screen.visibleFrame : NSMakeRect(0, 0, 1440, 900);
    CGFloat winW = 540;
    CGFloat winH = std::min((CGFloat)1000, visible.size.height * (CGFloat)0.96);
    NSRect frame = NSMakeRect(NSMaxX(visible) - winW - 40,
                              NSMaxY(visible) - winH - 12,
                              winW, winH);

    NSWindowStyleMask styleMask = (NSWindowStyleMaskTitled | NSWindowStyleMaskClosable |
                                   NSWindowStyleMaskMiniaturizable | NSWindowStyleMaskResizable);

    int sx = g_app.prefs.getInt(PREF_KEY_WINDOW_X, kGeomUnset);
    int sy = g_app.prefs.getInt(PREF_KEY_WINDOW_Y, kGeomUnset);
    int sw = g_app.prefs.getInt(PREF_KEY_WINDOW_W, kGeomUnset);
    int sh = g_app.prefs.getInt(PREF_KEY_WINDOW_H, kGeomUnset);
    if (sx != kGeomUnset && sy != kGeomUnset && sw > 0 && sh > 0) {
        NSRect saved = NSMakeRect(sx, sy, sw, sh);
        if (fitSavedFrameToScreens(&saved, styleMask)) frame = saved;
    }

    self.window = [[NSWindow alloc]
        initWithContentRect:frame
                  styleMask:styleMask
                    backing:NSBackingStoreBuffered
                      defer:NO];
    self.window.title = @"DietSentry";

    g_device = MTLCreateSystemDefaultDevice();
    if (!g_device) {
        NSAlert* alert = [[NSAlert alloc] init];
        alert.messageText = @"DietSentry";
        alert.informativeText = @"Metal is not supported on this Mac.";
        [alert runModal];
        [NSApp terminate:nil];
        return;
    }
    g_commandQueue = [g_device newCommandQueue];

    g_view = [[MTKView alloc] initWithFrame:frame device:g_device];
    g_view.clearColor = MTLClearColorMake(0.99, 0.97, 1.0, 1.0);
    self.renderer = [[DSRenderer alloc] init];
    g_view.delegate = self.renderer;
    self.window.contentView = g_view;

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;
    io.IniFilename = nullptr;

    ImGui_ImplMetal_Init(g_device);
    ImGui_ImplOSX_Init(g_view);

    // AppKit points are density-independent; Retina sharpness comes from the
    // backend's framebuffer scale, so dp() maps 1:1.
    g_app.uiScale = 1.0f;
    // Symbol fallbacks (the Windows port gets all of these from Segoe UI Symbol):
    // Apple Symbols supplies ⚙ ‹ › ← →; Menlo (always present, DejaVu-derived) backfills
    // the Dingbats glyphs neither Arial nor Apple Symbols has — ✕ (clear buttons) and
    // ➤ (AI send) — which otherwise render as ImGui's "?" fallback glyph.
    static const std::initializer_list<const char*> kSymbolFallbacks = {
        "/System/Library/Fonts/Apple Symbols.ttf", "/System/Library/Fonts/Menlo.ttc"};
    g_app.fontRegular = loadFontOrDefault(io,
        {"/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Helvetica.ttc"},
        kSymbolFallbacks);
    g_app.fontBold = loadFontOrDefault(io,
        {"/System/Library/Fonts/Supplemental/Arial Bold.ttf", "/System/Library/Fonts/Helvetica.ttc"},
        kSymbolFallbacks);
    g_app.fontMono = loadFontOrDefault(io,
        {"/System/Library/Fonts/Menlo.ttc", "/System/Library/Fonts/Supplemental/Courier New.ttf"});
    io.FontDefault = g_app.fontRegular;

    ui::applyTheme(g_app);
    if (!g_app.db.open()) {
        NSAlert* alert = [[NSAlert alloc] init];
        alert.messageText = @"DietSentry";
        alert.informativeText = @"Could not open or create foods.db.\n"
                                 "Make sure the assets folder (with foods.db) sits next to the "
                                 "binary or inside the app bundle's Resources.";
        [alert runModal];
        [NSApp terminate:nil];
        return;
    }
    loadPrompts(g_app);
    g_app.nav.emplace_back(makeFoodSearchScreen(g_app));
    autoNavigate(g_app);

    [self.window makeKeyAndOrderFront:nil];
    [NSApp activateIgnoringOtherApps:YES];
}

- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication*)sender {
    (void)sender;
    return YES;
}

- (BOOL)applicationSupportsSecureRestorableState:(NSApplication*)app {
    (void)app;
    return YES;
}

// Saved once, at quit, rather than from windowDidResize:/windowDidMove: —
// every Prefs::put* rewrites prefs.json, so tracking a live drag would mean
// hundreds of file writes. Fires for both Cmd+Q and closing the last window.
- (void)saveWindowGeometry {
    if (!self.window) return;
    // While full-screen the frame is the whole display; keep the size the user
    // actually chose so leaving full-screen next launch restores that instead.
    if (self.window.styleMask & NSWindowStyleMaskFullScreen) return;

    NSRect c = [self.window contentRectForFrameRect:self.window.frame];
    g_app.prefs.putInt(PREF_KEY_WINDOW_X, (int)lround(c.origin.x));
    g_app.prefs.putInt(PREF_KEY_WINDOW_Y, (int)lround(c.origin.y));
    g_app.prefs.putInt(PREF_KEY_WINDOW_W, (int)lround(c.size.width));
    g_app.prefs.putInt(PREF_KEY_WINDOW_H, (int)lround(c.size.height));
}

- (void)applicationWillTerminate:(NSNotification*)notification {
    (void)notification;
    [self saveWindowGeometry];
    g_app.db.close();
    ImGui_ImplMetal_Shutdown();
    ImGui_ImplOSX_Shutdown();
    ImGui::DestroyContext();
}

@end

int main(int argc, const char* argv[]) {
    (void)argc; (void)argv;
    @autoreleasepool {
        NSApplication* application = NSApplication.sharedApplication;
        DSAppDelegate* delegate = [[DSAppDelegate alloc] init];
        application.delegate = delegate;
        [application run];
    }
    return 0;
}
