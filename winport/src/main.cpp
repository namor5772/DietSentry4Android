// main.cpp — WinMain, D3D11 + ImGui bootstrap, navigation host.
#include "app.h"
#include "ui.h"
#include "imgui_impl_win32.h"
#include "imgui_impl_dx11.h"
#include <d3d11.h>
#include <tchar.h>

static ID3D11Device* g_pd3dDevice = nullptr;
static ID3D11DeviceContext* g_pd3dDeviceContext = nullptr;
static IDXGISwapChain* g_pSwapChain = nullptr;
static ID3D11RenderTargetView* g_mainRenderTargetView = nullptr;
static bool g_SwapChainOccluded = false;
static UINT g_ResizeWidth = 0, g_ResizeHeight = 0;

extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(HWND, UINT, WPARAM, LPARAM);

static bool CreateDeviceD3D(HWND hWnd);
static void CleanupDeviceD3D();
static void CreateRenderTarget();
static void CleanupRenderTarget();
static LRESULT WINAPI WndProc(HWND, UINT, WPARAM, LPARAM);

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
    if (!g_pd3dDevice) return nullptr;
    D3D11_TEXTURE2D_DESC desc = {};
    desc.Width = w;
    desc.Height = h;
    desc.MipLevels = 1;
    desc.ArraySize = 1;
    desc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    desc.SampleDesc.Count = 1;
    desc.Usage = D3D11_USAGE_DEFAULT;
    desc.BindFlags = D3D11_BIND_SHADER_RESOURCE;
    D3D11_SUBRESOURCE_DATA sub = {};
    sub.pSysMem = rgba;
    sub.SysMemPitch = w * 4;
    ID3D11Texture2D* tex = nullptr;
    if (FAILED(g_pd3dDevice->CreateTexture2D(&desc, &sub, &tex))) return nullptr;
    D3D11_SHADER_RESOURCE_VIEW_DESC srvDesc = {};
    srvDesc.Format = desc.Format;
    srvDesc.ViewDimension = D3D11_SRV_DIMENSION_TEXTURE2D;
    srvDesc.Texture2D.MipLevels = 1;
    ID3D11ShaderResourceView* srv = nullptr;
    HRESULT hr = g_pd3dDevice->CreateShaderResourceView(tex, &srvDesc, &srv);
    tex->Release();
    return SUCCEEDED(hr) ? srv : nullptr;
}

void App::releaseTexture(void* srv) {
    if (srv) ((ID3D11ShaderResourceView*)srv)->Release();
}

static void loadPrompts(App& app) {
    std::wstring assets = exeDirectory() + L"\\assets\\";
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

static ImFont* loadFontOrDefault(ImGuiIO& io, const char* path, const char* mergePath = nullptr) {
    ImFont* f = nullptr;
    if (GetFileAttributesA(path) != INVALID_FILE_ATTRIBUTES)
        f = io.Fonts->AddFontFromFileTTF(path);
    if (f && mergePath && GetFileAttributesA(mergePath) != INVALID_FILE_ATTRIBUTES) {
        ImFontConfig cfg;
        cfg.MergeMode = true;
        io.Fonts->AddFontFromFileTTF(mergePath, 0.0f, &cfg);
    }
    if (!f) f = io.Fonts->AddFontDefault();
    return f;
}

int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
    ImGui_ImplWin32_EnableDpiAwareness();

    WNDCLASSEXW wc = {sizeof(wc), CS_CLASSDC, WndProc, 0L, 0L,
                      GetModuleHandle(nullptr), nullptr, LoadCursor(nullptr, IDC_ARROW), nullptr, nullptr,
                      L"DietSentry", nullptr};
    wc.hIcon = LoadIconW(wc.hInstance, MAKEINTRESOURCEW(1));
    wc.hIconSm = wc.hIcon;
    ::RegisterClassExW(&wc);

    float dpiScale = 1.0f;
    {
        HDC screen = GetDC(nullptr);
        dpiScale = GetDeviceCaps(screen, LOGPIXELSX) / 96.0f;
        ReleaseDC(nullptr, screen);
    }
    int winW = (int)(540 * dpiScale), winH = (int)(1000 * dpiScale);
    RECT wa;
    SystemParametersInfoW(SPI_GETWORKAREA, 0, &wa, 0);
    winH = std::min(winH, (int)((wa.bottom - wa.top) * 0.96f));
    HWND hwnd = ::CreateWindowW(wc.lpszClassName, L"DietSentry", WS_OVERLAPPEDWINDOW,
                                wa.right - winW - (int)(40 * dpiScale), wa.top + (int)(12 * dpiScale),
                                winW, winH, nullptr, nullptr, wc.hInstance, nullptr);

    if (!CreateDeviceD3D(hwnd)) {
        CleanupDeviceD3D();
        ::UnregisterClassW(wc.lpszClassName, wc.hInstance);
        return 1;
    }

    ::ShowWindow(hwnd, SW_SHOWDEFAULT);
    ::UpdateWindow(hwnd);

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;
    // Escape is this app's Back key. By default ImGui also treats an Escape that
    // nothing else consumes as "clear the focused item" while leaving the focus
    // cursor shown, after which Tab has no item to step from and does nothing
    // until an arrow key or the mouse is used. Keep the focus where it is.
    io.ConfigNavEscapeClearFocusItem = false;
    io.IniFilename = nullptr;

    ImGui_ImplWin32_Init(hwnd);
    ImGui_ImplDX11_Init(g_pd3dDevice, g_pd3dDeviceContext);

    static App app;
    app.uiScale = dpiScale;
    app.fontRegular = loadFontOrDefault(io, "C:\\Windows\\Fonts\\segoeui.ttf", "C:\\Windows\\Fonts\\seguisym.ttf");
    app.fontBold = loadFontOrDefault(io, "C:\\Windows\\Fonts\\segoeuib.ttf", "C:\\Windows\\Fonts\\seguisym.ttf");
    app.fontMono = loadFontOrDefault(io, "C:\\Windows\\Fonts\\consola.ttf");
    io.FontDefault = app.fontRegular;

    ui::applyTheme(app);
    app.prefs.load();
    if (!app.db.open()) {
        MessageBoxW(hwnd, L"Could not open or create foods.db.\nMake sure assets\\foods.db sits next to the exe.",
                    L"DietSentry", MB_ICONERROR);
        return 1;
    }
    loadPrompts(app);
    app.nav.emplace_back(makeFoodSearchScreen(app));

    // Test/deep-link hook: DIETSENTRY_AUTONAV=<route> opens a screen at launch.
    {
        char route[64] = {0};
        DWORD n = GetEnvironmentVariableA("DIETSENTRY_AUTONAV", route, sizeof(route));
        if (n > 0 && n < sizeof(route)) {
            std::string r = route;
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
    }

    bool done = false;
    while (!done) {
        MSG msg;
        while (::PeekMessage(&msg, nullptr, 0U, 0U, PM_REMOVE)) {
            ::TranslateMessage(&msg);
            ::DispatchMessage(&msg);
            if (msg.message == WM_QUIT) done = true;
        }
        if (done) break;
        if (app.quitRequested) break;

        if (g_SwapChainOccluded && g_pSwapChain->Present(0, DXGI_PRESENT_TEST) == DXGI_STATUS_OCCLUDED) {
            ::Sleep(10);
            continue;
        }
        g_SwapChainOccluded = false;

        if (g_ResizeWidth != 0 && g_ResizeHeight != 0) {
            CleanupRenderTarget();
            g_pSwapChain->ResizeBuffers(0, g_ResizeWidth, g_ResizeHeight, DXGI_FORMAT_UNKNOWN, 0);
            g_ResizeWidth = g_ResizeHeight = 0;
            CreateRenderTarget();
        }

        ImGui_ImplDX11_NewFrame();
        ImGui_ImplWin32_NewFrame();
        ImGui::NewFrame();

#ifdef DEBUG_SIZES
        {
            static int frameCount = 0;
            if (++frameCount == 60) {
                RECT rc;
                GetClientRect(hwnd, &rc);
                char buf[256];
                snprintf(buf, sizeof(buf), "display=%.0fx%.0f client=%ldx%ld dpiScale=%.2f dpiWin=%.2f\n",
                         ImGui::GetIO().DisplaySize.x, ImGui::GetIO().DisplaySize.y,
                         rc.right, rc.bottom, dpiScale,
                         ImGui_ImplWin32_GetDpiScaleForHwnd(hwnd));
                FILE* f = nullptr;
                fopen_s(&f, "debug_sizes.txt", "w");
                if (f) { fputs(buf, f); fclose(f); }
            }
        }
#endif

        // Root window fills the viewport; the active screen draws into it.
        ImGuiViewport* vp = ImGui::GetMainViewport();
        ImGui::SetNextWindowPos(vp->Pos);
        ImGui::SetNextWindowSize(vp->Size);
        ImGui::Begin("##root", nullptr,
                     ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
                     ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoBringToFrontOnFocus |
                     ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoScrollWithMouse |
                     ImGuiWindowFlags_NoSavedSettings);
        if (!app.nav.empty()) app.nav.back()->draw(app);
        app.endFrameKeyboardNav();
        ImGui::End();

        app.drawToasts();

        // Apply deferred navigation mutations
        for (auto& op : app.pendingNavOps) op();
        app.pendingNavOps.clear();

        ImGui::Render();
        const float clear[4] = {0.99f, 0.97f, 1.0f, 1.0f};
        g_pd3dDeviceContext->OMSetRenderTargets(1, &g_mainRenderTargetView, nullptr);
        g_pd3dDeviceContext->ClearRenderTargetView(g_mainRenderTargetView, clear);
        ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());

        HRESULT hr = g_pSwapChain->Present(1, 0);
        g_SwapChainOccluded = (hr == DXGI_STATUS_OCCLUDED);
    }

    app.db.close();
    ImGui_ImplDX11_Shutdown();
    ImGui_ImplWin32_Shutdown();
    ImGui::DestroyContext();
    CleanupDeviceD3D();
    ::DestroyWindow(hwnd);
    ::UnregisterClassW(wc.lpszClassName, wc.hInstance);
    return 0;
}

// ---------------------------------------------------------------------------
static bool CreateDeviceD3D(HWND hWnd) {
    DXGI_SWAP_CHAIN_DESC sd = {};
    sd.BufferCount = 2;
    sd.BufferDesc.Width = 0;
    sd.BufferDesc.Height = 0;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferDesc.RefreshRate.Numerator = 60;
    sd.BufferDesc.RefreshRate.Denominator = 1;
    sd.Flags = DXGI_SWAP_CHAIN_FLAG_ALLOW_MODE_SWITCH;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = hWnd;
    sd.SampleDesc.Count = 1;
    sd.SampleDesc.Quality = 0;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;

    UINT createDeviceFlags = 0;
    D3D_FEATURE_LEVEL featureLevel;
    const D3D_FEATURE_LEVEL featureLevelArray[2] = {D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_0};
    HRESULT res = D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr,
        createDeviceFlags, featureLevelArray, 2, D3D11_SDK_VERSION, &sd, &g_pSwapChain,
        &g_pd3dDevice, &featureLevel, &g_pd3dDeviceContext);
    if (res == DXGI_ERROR_UNSUPPORTED)
        res = D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_WARP, nullptr,
            createDeviceFlags, featureLevelArray, 2, D3D11_SDK_VERSION, &sd, &g_pSwapChain,
            &g_pd3dDevice, &featureLevel, &g_pd3dDeviceContext);
    if (res != S_OK) return false;
    CreateRenderTarget();
    return true;
}

static void CleanupDeviceD3D() {
    CleanupRenderTarget();
    if (g_pSwapChain) { g_pSwapChain->Release(); g_pSwapChain = nullptr; }
    if (g_pd3dDeviceContext) { g_pd3dDeviceContext->Release(); g_pd3dDeviceContext = nullptr; }
    if (g_pd3dDevice) { g_pd3dDevice->Release(); g_pd3dDevice = nullptr; }
}

static void CreateRenderTarget() {
    ID3D11Texture2D* pBackBuffer;
    g_pSwapChain->GetBuffer(0, IID_PPV_ARGS(&pBackBuffer));
    g_pd3dDevice->CreateRenderTargetView(pBackBuffer, nullptr, &g_mainRenderTargetView);
    pBackBuffer->Release();
}

static void CleanupRenderTarget() {
    if (g_mainRenderTargetView) { g_mainRenderTargetView->Release(); g_mainRenderTargetView = nullptr; }
}

static LRESULT WINAPI WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (ImGui_ImplWin32_WndProcHandler(hWnd, msg, wParam, lParam)) return true;
    switch (msg) {
    case WM_SIZE:
        if (wParam == SIZE_MINIMIZED) return 0;
        g_ResizeWidth = (UINT)LOWORD(lParam);
        g_ResizeHeight = (UINT)HIWORD(lParam);
        return 0;
    case WM_SYSCOMMAND:
        if ((wParam & 0xfff0) == SC_KEYMENU) return 0; // disable ALT menu
        break;
    case WM_DESTROY:
        ::PostQuitMessage(0);
        return 0;
    }
    return ::DefWindowProcW(hWnd, msg, wParam, lParam);
}
