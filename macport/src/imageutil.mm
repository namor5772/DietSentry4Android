// imageutil.mm — ImageIO decode/scale/encode + NSOpenPanel pickers.
// macOS counterpart of winport/src/imageutil.cpp (WIC + IFileDialog).
#include "imageutil.h"
#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <ImageIO/ImageIO.h>
#import <CoreGraphics/CoreGraphics.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>

// Implemented in main.mm: pauses the MTKView render loop while a modal
// NSOpenPanel spins its own run loop, so ImGui frames can't re-enter.
void macSetRenderPaused(bool paused);

static NSURL* urlFromWide(const std::wstring& path) {
    NSString* s = [NSString stringWithUTF8String:wideToUtf8(path).c_str()];
    return s ? [NSURL fileURLWithPath:s] : nil;
}

std::optional<LoadedAiImage> loadImageForAi(App& app, const std::wstring& path, std::string& error) {
    NSURL* url = urlFromWide(path);
    if (!url) { error = "Bad file path."; return std::nullopt; }

    CGImageSourceRef source = CGImageSourceCreateWithURL((__bridge CFURLRef)url, nullptr);
    if (!source) { error = "Image could not be decoded."; return std::nullopt; }

    // Decode + EXIF-rotate + clamp longest side to AI_IMAGE_MAX_DIM in one step.
    NSDictionary* thumbOpts = @{
        (id)kCGImageSourceCreateThumbnailFromImageAlways : @YES,
        (id)kCGImageSourceCreateThumbnailWithTransform : @YES,
        (id)kCGImageSourceThumbnailMaxPixelSize : @(AI_IMAGE_MAX_DIM),
    };
    CGImageRef image = CGImageSourceCreateThumbnailAtIndex(source, 0, (__bridge CFDictionaryRef)thumbOpts);
    CFRelease(source);
    if (!image) { error = "Image frame missing."; return std::nullopt; }

    size_t outW = CGImageGetWidth(image);
    size_t outH = CGImageGetHeight(image);
    if (outW == 0 || outH == 0) {
        CGImageRelease(image);
        error = "Image header could not be decoded.";
        return std::nullopt;
    }

    LoadedAiImage out;

    // JPEG-encode to memory @ 0.85 quality (matches loadImageForAi on Android
    // and the WIC encoder on Windows).
    {
        NSMutableData* jpegData = [NSMutableData data];
        CGImageDestinationRef dest = CGImageDestinationCreateWithData(
            (__bridge CFMutableDataRef)jpegData, (__bridge CFStringRef)UTTypeJPEG.identifier, 1, nullptr);
        if (!dest) {
            CGImageRelease(image);
            error = "JPEG encoder failed.";
            return std::nullopt;
        }
        NSDictionary* props = @{(id)kCGImageDestinationLossyCompressionQuality : @0.85};
        CGImageDestinationAddImage(dest, image, (__bridge CFDictionaryRef)props);
        bool okEnc = CGImageDestinationFinalize(dest);
        CFRelease(dest);
        if (!okEnc || jpegData.length == 0) {
            CGImageRelease(image);
            error = "JPEG encoding failed.";
            return std::nullopt;
        }
        out.jpegBytes.assign((const unsigned char*)jpegData.bytes,
                             (const unsigned char*)jpegData.bytes + jpegData.length);
    }

    // Thumbnail texture (RGBA8)
    {
        std::vector<unsigned char> rgba(outW * outH * 4);
        CGColorSpaceRef cs = CGColorSpaceCreateDeviceRGB();
        CGContextRef ctx = CGBitmapContextCreate(rgba.data(), outW, outH, 8, outW * 4, cs,
                                                 kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
        CGColorSpaceRelease(cs);
        if (ctx) {
            CGContextDrawImage(ctx, CGRectMake(0, 0, outW, outH), image);
            CGContextRelease(ctx);
            out.texture = app.createTextureRGBA(rgba.data(), (int)outW, (int)outH);
            out.width = (int)outW;
            out.height = (int)outH;
        }
    }
    CGImageRelease(image);
    return out;
}

std::vector<std::wstring> pickImageFiles() {
    std::vector<std::wstring> out;
    @autoreleasepool {
        NSOpenPanel* panel = [NSOpenPanel openPanel];
        panel.canChooseFiles = YES;
        panel.canChooseDirectories = NO;
        panel.allowsMultipleSelection = YES;
        panel.allowedContentTypes = @[ UTTypeImage ];
        macSetRenderPaused(true);
        NSModalResponse resp = [panel runModal];
        macSetRenderPaused(false);
        if (resp == NSModalResponseOK) {
            for (NSURL* url in panel.URLs) {
                const char* p = url.path.UTF8String;
                if (p) out.push_back(utf8ToWide(p));
            }
        }
    }
    return out;
}

std::optional<std::wstring> pickFolder(const std::wstring& initial) {
    std::optional<std::wstring> result;
    @autoreleasepool {
        NSOpenPanel* panel = [NSOpenPanel openPanel];
        panel.canChooseFiles = NO;
        panel.canChooseDirectories = YES;
        panel.canCreateDirectories = YES;
        panel.allowsMultipleSelection = NO;
        if (!initial.empty()) {
            NSURL* url = urlFromWide(initial);
            if (url) panel.directoryURL = url;
        }
        macSetRenderPaused(true);
        NSModalResponse resp = [panel runModal];
        macSetRenderPaused(false);
        if (resp == NSModalResponseOK && panel.URL != nil) {
            const char* p = panel.URL.path.UTF8String;
            if (p) result = utf8ToWide(p);
        }
    }
    return result;
}
