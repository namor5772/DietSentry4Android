// imageutil.h — WIC-based image loading for AI attachments (loadImageForAi):
// decode any picture format, downscale to <= AI_IMAGE_MAX_DIM, JPEG-encode,
// and build a D3D thumbnail texture for the chat UI.
#pragma once
#include "app.h"

struct LoadedAiImage {
    std::vector<unsigned char> jpegBytes;   // JPEG @ ~85 quality
    std::string mediaType = "image/jpeg";
    void* texture = nullptr;                // ID3D11ShaderResourceView* for thumbnails
    int width = 0, height = 0;
};

// Returns nullopt with `error` set on failure.
std::optional<LoadedAiImage> loadImageForAi(App& app, const std::wstring& path, std::string& error);

// Multi-select image file picker (the PickMultipleVisualMedia equivalent).
std::vector<std::wstring> pickImageFiles();
