// imageutil.cpp — WIC decode/scale/encode + file picker.
#include "imageutil.h"
#include <wincodec.h>
#include <shobjidl.h>
#include <propvarutil.h>

template <typename T>
struct ComPtrLite {
    T* p = nullptr;
    ~ComPtrLite() { if (p) p->Release(); }
    T** operator&() { return &p; }
    T* operator->() { return p; }
    operator T*() { return p; }
};

static IWICImagingFactory* wicFactory() {
    static IWICImagingFactory* factory = nullptr;
    if (!factory) {
        CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
                         IID_PPV_ARGS(&factory));
    }
    return factory;
}

std::optional<LoadedAiImage> loadImageForAi(App& app, const std::wstring& path, std::string& error) {
    IWICImagingFactory* factory = wicFactory();
    if (!factory) { error = "WIC unavailable"; return std::nullopt; }

    ComPtrLite<IWICBitmapDecoder> decoder;
    if (FAILED(factory->CreateDecoderFromFilename(path.c_str(), nullptr, GENERIC_READ,
                                                  WICDecodeMetadataCacheOnDemand, &decoder))) {
        error = "Image could not be decoded.";
        return std::nullopt;
    }
    ComPtrLite<IWICBitmapFrameDecode> frame;
    if (FAILED(decoder->GetFrame(0, &frame))) { error = "Image frame missing."; return std::nullopt; }
    UINT w = 0, h = 0;
    frame->GetSize(&w, &h);
    if (w == 0 || h == 0) { error = "Image header could not be decoded."; return std::nullopt; }

    UINT outW = w, outH = h;
    if ((int)w > AI_IMAGE_MAX_DIM || (int)h > AI_IMAGE_MAX_DIM) {
        double scale = (double)AI_IMAGE_MAX_DIM / (w > h ? w : h);
        outW = (UINT)(w * scale);
        outH = (UINT)(h * scale);
    }

    IWICBitmapSource* source = frame;
    ComPtrLite<IWICBitmapScaler> scaler;
    if (outW != w || outH != h) {
        if (FAILED(factory->CreateBitmapScaler(&scaler)) ||
            FAILED(scaler->Initialize(frame, outW, outH, WICBitmapInterpolationModeFant))) {
            error = "Image scaling failed.";
            return std::nullopt;
        }
        source = scaler;
    }

    // 24bpp BGR for JPEG encoding
    ComPtrLite<IWICFormatConverter> bgrConv;
    if (FAILED(factory->CreateFormatConverter(&bgrConv)) ||
        FAILED(bgrConv->Initialize(source, GUID_WICPixelFormat24bppBGR,
                                   WICBitmapDitherTypeNone, nullptr, 0.0,
                                   WICBitmapPaletteTypeCustom))) {
        error = "Pixel conversion failed.";
        return std::nullopt;
    }

    // Encode JPEG to memory stream
    ComPtrLite<IStream> stream;
    if (FAILED(CreateStreamOnHGlobal(nullptr, TRUE, &stream))) {
        error = "Stream allocation failed.";
        return std::nullopt;
    }
    {
        ComPtrLite<IWICBitmapEncoder> encoder;
        if (FAILED(factory->CreateEncoder(GUID_ContainerFormatJpeg, nullptr, &encoder)) ||
            FAILED(encoder->Initialize(stream, WICBitmapEncoderNoCache))) {
            error = "JPEG encoder failed.";
            return std::nullopt;
        }
        ComPtrLite<IWICBitmapFrameEncode> frameEncode;
        ComPtrLite<IPropertyBag2> props;
        if (FAILED(encoder->CreateNewFrame(&frameEncode, &props))) {
            error = "JPEG frame failed.";
            return std::nullopt;
        }
        PROPBAG2 opt = {};
        wchar_t optName[] = L"ImageQuality";
        opt.pstrName = optName;
        VARIANT v;
        VariantInit(&v);
        v.vt = VT_R4;
        v.fltVal = 0.85f;
        props.p->Write(1, &opt, &v);
        if (FAILED(frameEncode->Initialize(props)) ||
            FAILED(frameEncode->WriteSource(bgrConv, nullptr)) ||
            FAILED(frameEncode->Commit()) || FAILED(encoder->Commit())) {
            error = "JPEG encoding failed.";
            return std::nullopt;
        }
    }
    HGLOBAL hg = nullptr;
    GetHGlobalFromStream(stream, &hg);
    SIZE_T size = GlobalSize(hg);
    void* mem = GlobalLock(hg);
    LoadedAiImage out;
    out.jpegBytes.assign((unsigned char*)mem, (unsigned char*)mem + size);
    GlobalUnlock(hg);

    // Thumbnail texture (RGBA)
    ComPtrLite<IWICFormatConverter> rgbaConv;
    if (SUCCEEDED(factory->CreateFormatConverter(&rgbaConv)) &&
        SUCCEEDED(rgbaConv->Initialize(source, GUID_WICPixelFormat32bppRGBA,
                                       WICBitmapDitherTypeNone, nullptr, 0.0,
                                       WICBitmapPaletteTypeCustom))) {
        std::vector<unsigned char> rgba((size_t)outW * outH * 4);
        if (SUCCEEDED(rgbaConv->CopyPixels(nullptr, outW * 4, (UINT)rgba.size(), rgba.data()))) {
            out.texture = app.createTextureRGBA(rgba.data(), (int)outW, (int)outH);
            out.width = (int)outW;
            out.height = (int)outH;
        }
    }
    return out;
}

std::vector<std::wstring> pickImageFiles() {
    std::vector<std::wstring> out;
    ComPtrLite<IFileOpenDialog> dlg;
    if (FAILED(CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER,
                                IID_PPV_ARGS(&dlg))))
        return out;
    DWORD opts = 0;
    dlg->GetOptions(&opts);
    dlg->SetOptions(opts | FOS_ALLOWMULTISELECT | FOS_FORCEFILESYSTEM | FOS_FILEMUSTEXIST);
    COMDLG_FILTERSPEC filters[] = {
        {L"Images", L"*.jpg;*.jpeg;*.png;*.bmp;*.gif;*.webp;*.heic;*.tif;*.tiff"},
        {L"All files", L"*.*"},
    };
    dlg->SetFileTypes(2, filters);
    if (FAILED(dlg->Show(nullptr))) return out;
    ComPtrLite<IShellItemArray> items;
    if (FAILED(dlg->GetResults(&items))) return out;
    DWORD count = 0;
    items->GetCount(&count);
    for (DWORD i = 0; i < count; i++) {
        IShellItem* item = nullptr;
        if (SUCCEEDED(items->GetItemAt(i, &item))) {
            PWSTR path = nullptr;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                out.push_back(path);
                CoTaskMemFree(path);
            }
            item->Release();
        }
    }
    return out;
}
