// imageutil.h — ImageIO-based image loading for AI attachments (loadImageForAi):
// decode any picture format, downscale to <= AI_IMAGE_MAX_DIM, JPEG-encode,
// and build a Metal thumbnail texture for the chat UI. Plus the native
// NSOpenPanel pickers used by the AI screen (images) and Utilities (folder).
#pragma once
#include "app.h"

struct LoadedAiImage {
    std::vector<unsigned char> jpegBytes;   // JPEG @ ~85 quality
    std::string mediaType = "image/jpeg";
    void* texture = nullptr;                // retained id<MTLTexture> for thumbnails
    int width = 0, height = 0;
};

// Returns nullopt with `error` set on failure.
std::optional<LoadedAiImage> loadImageForAi(App& app, const std::wstring& path, std::string& error);

// Multi-select image file picker (the PickMultipleVisualMedia equivalent).
std::vector<std::wstring> pickImageFiles();

// Folder picker (Utilities exchange-folder flow; IFileDialog equivalent).
std::optional<std::wstring> pickFolder(const std::wstring& initial);
