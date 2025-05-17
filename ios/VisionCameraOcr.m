// #import <Foundation/Foundation.h>
// #import <VisionCamera/FrameProcessorPlugin.h>

// @interface VISION_EXPORT_SWIFT_FRAME_PROCESSOR(scanOCR, OCRFrameProcessorPlugin)
// @end
#import <Foundation/Foundation.h>
#import <VisionCamera/FrameProcessorPlugin.h>
#import <VisionCamera/FrameProcessorPluginRegistry.h>
#import <VisionCamera/Frame.h>
#import "VisionCameraOcr-Swift.h"
#import "VisionCameraOcr-Bridging-Header.h"
/*@interface VisionCameraOcr : FrameProcessorPlugin
@end
VISION_EXPORT_SWIFT_FRAME_PROCESSOR(VisionCameraOcr, scanOCR)*/

@interface VisionCameraOcr (FrameProcessorPluginLoader)
@end

@implementation VisionCameraOcr (FrameProcessorPluginLoader)

+ (void)load
{
    [FrameProcessorPluginRegistry addFrameProcessorPlugin:@"scanOCR"
                                        withInitializer:^FrameProcessorPlugin* (VisionCameraProxyHolder* proxy, NSDictionary* options) {
        return [[VisionCameraOcr alloc] initWithProxy:proxy withOptions:options];
    }];
}

@end
