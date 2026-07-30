// draw_icon.m — renders the DietSentry macOS app icon (1024x1024 PNG).
// Big Sur-style rounded square on Material purple, white dinner plate,
// nutrition bar chart on the plate. Usage: draw_icon <out.png>
#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>
#import <ImageIO/ImageIO.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>

static void addRoundedRect(CGContextRef ctx, CGRect r, CGFloat rad) {
    CGPathRef p = CGPathCreateWithRoundedRect(r, rad, rad, NULL);
    CGContextAddPath(ctx, p);
    CGPathRelease(p);
}

int main(int argc, const char* argv[]) {
    @autoreleasepool {
        if (argc < 2) { fprintf(stderr, "usage: draw_icon <out.png>\n"); return 1; }
        const size_t S = 1024;
        CGColorSpaceRef cs = CGColorSpaceCreateDeviceRGB();
        CGContextRef ctx = CGBitmapContextCreate(NULL, S, S, 8, S * 4, cs,
                                                 kCGImageAlphaPremultipliedLast);
        CGColorSpaceRelease(cs);
        if (!ctx) { fprintf(stderr, "no context\n"); return 1; }

        // --- Background squircle (Apple icon grid: 824pt centered) ---
        CGRect tile = CGRectMake(100, 100, 824, 824);
        CGFloat corner = 185;

        // soft drop shadow behind the tile
        CGContextSaveGState(ctx);
        CGContextSetShadowWithColor(ctx, CGSizeMake(0, -14), 36,
            CGColorCreateGenericRGB(0, 0, 0, 0.30));
        addRoundedRect(ctx, tile, corner);
        CGContextSetRGBFillColor(ctx, 0x67/255.0, 0x50/255.0, 0xA4/255.0, 1.0);
        CGContextFillPath(ctx);
        CGContextRestoreGState(ctx);

        // vertical gradient over the tile (lighter top -> deeper bottom)
        CGContextSaveGState(ctx);
        addRoundedRect(ctx, tile, corner);
        CGContextClip(ctx);
        {
            CGColorSpaceRef gcs = CGColorSpaceCreateDeviceRGB();
            CGFloat comps[8] = {
                0x7C/255.0, 0x66/255.0, 0xBB/255.0, 1.0,   // top   #7C66BB
                0x53/255.0, 0x3E/255.0, 0x8E/255.0, 1.0    // bottom #533E8E
            };
            CGFloat locs[2] = {0.0, 1.0};
            CGGradientRef grad = CGGradientCreateWithColorComponents(gcs, comps, locs, 2);
            CGContextDrawLinearGradient(ctx, grad, CGPointMake(512, 924), CGPointMake(512, 100), 0);
            CGGradientRelease(grad);
            CGColorSpaceRelease(gcs);
        }
        // subtle top sheen
        {
            CGColorSpaceRef gcs = CGColorSpaceCreateDeviceRGB();
            CGFloat comps[8] = { 1, 1, 1, 0.14,   1, 1, 1, 0.0 };
            CGFloat locs[2] = {0.0, 1.0};
            CGGradientRef grad = CGGradientCreateWithColorComponents(gcs, comps, locs, 2);
            CGContextDrawLinearGradient(ctx, grad, CGPointMake(512, 924), CGPointMake(512, 620), 0);
            CGGradientRelease(grad);
            CGColorSpaceRelease(gcs);
        }
        CGContextRestoreGState(ctx);

        // --- Dinner plate ---
        CGPoint c = CGPointMake(512, 512);
        CGFloat plateR = 300;
        CGContextSaveGState(ctx);
        CGContextSetShadowWithColor(ctx, CGSizeMake(0, -10), 30,
            CGColorCreateGenericRGB(0, 0, 0, 0.28));
        CGContextSetRGBFillColor(ctx, 1, 1, 1, 1);
        CGContextFillEllipseInRect(ctx,
            CGRectMake(c.x - plateR, c.y - plateR, plateR * 2, plateR * 2));
        CGContextRestoreGState(ctx);

        // plate well (inner circle): faint fill + rim line
        CGFloat wellR = 212;
        CGContextSetRGBFillColor(ctx, 0xF4/255.0, 0xF1/255.0, 0xF9/255.0, 1.0);
        CGContextFillEllipseInRect(ctx,
            CGRectMake(c.x - wellR, c.y - wellR, wellR * 2, wellR * 2));
        CGContextSetRGBStrokeColor(ctx, 0xD9/255.0, 0xD2/255.0, 0xE5/255.0, 1.0);
        CGContextSetLineWidth(ctx, 6);
        CGContextStrokeEllipseInRect(ctx,
            CGRectMake(c.x - wellR, c.y - wellR, wellR * 2, wellR * 2));

        // --- Nutrition bar chart on the plate (3 tonal purple bars) ---
        struct { CGFloat h; CGFloat r, g, b; } bars[3] = {
            {150, 0xB6/255.0, 0x9D/255.0, 0xF8/255.0},   // #B69DF8
            {235, 0x67/255.0, 0x50/255.0, 0xA4/255.0},   // #6750A4
            {185, 0x8B/255.0, 0x75/255.0, 0xC9/255.0},   // #8B75C9
        };
        CGFloat barW = 62, gap = 34;
        CGFloat totalW = 3 * barW + 2 * gap;
        CGFloat x0 = c.x - totalW / 2;
        CGFloat baseY = c.y - 115;
        for (int i = 0; i < 3; i++) {
            CGRect r = CGRectMake(x0 + i * (barW + gap), baseY, barW, bars[i].h);
            CGContextSetRGBFillColor(ctx, bars[i].r, bars[i].g, bars[i].b, 1.0);
            CGPathRef p = CGPathCreateWithRoundedRect(r, barW * 0.28, barW * 0.28, NULL);
            CGContextAddPath(ctx, p);
            CGContextFillPath(ctx);
            CGPathRelease(p);
        }
        // baseline under the bars
        CGContextSetRGBStrokeColor(ctx, 0x67/255.0, 0x50/255.0, 0xA4/255.0, 0.55);
        CGContextSetLineWidth(ctx, 10);
        CGContextSetLineCap(ctx, kCGLineCapRound);
        CGContextMoveToPoint(ctx, c.x - totalW / 2 - 26, baseY - 24);
        CGContextAddLineToPoint(ctx, c.x + totalW / 2 + 26, baseY - 24);
        CGContextStrokePath(ctx);

        // --- Write PNG ---
        CGImageRef img = CGBitmapContextCreateImage(ctx);
        NSURL* url = [NSURL fileURLWithPath:[NSString stringWithUTF8String:argv[1]]];
        CGImageDestinationRef dest = CGImageDestinationCreateWithURL(
            (__bridge CFURLRef)url, (__bridge CFStringRef)UTTypePNG.identifier, 1, NULL);
        CGImageDestinationAddImage(dest, img, NULL);
        bool ok = CGImageDestinationFinalize(dest);
        CFRelease(dest);
        CGImageRelease(img);
        CGContextRelease(ctx);
        if (!ok) { fprintf(stderr, "write failed\n"); return 1; }
        printf("wrote %s\n", argv[1]);
        return 0;
    }
}
