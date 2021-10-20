package com.kuili.takephoto.utils;

import ohos.aafwk.ability.DataAbilityHelper;
import ohos.app.Context;
import ohos.data.rdb.ValuesBucket;
import ohos.media.image.ImagePacker;
import ohos.media.image.PixelMap;
import ohos.media.photokit.metadata.AVStorage;
import ohos.utils.net.Uri;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class GalleryUtil {
    private GalleryUtil() {}

    public static void saveToGallery(Context context, String fileName, PixelMap pixelMap) {
        try {
            ValuesBucket valuesBucket = new ValuesBucket();
            valuesBucket.putString(AVStorage.Images.Media.DISPLAY_NAME, fileName);
            valuesBucket.putString("relative_path", "DCIM/");
            valuesBucket.putString(AVStorage.Images.Media.MIME_TYPE, "image/JPEG");
            //应用独占
            valuesBucket.putInteger("is_pending", 1);
            DataAbilityHelper helper = DataAbilityHelper.creator(context);
            int id = helper.insert(AVStorage.Images.Media.EXTERNAL_DATA_ABILITY_URI, valuesBucket);
            Uri uri = Uri.appendEncodedPathToUri(AVStorage.Images.Media.EXTERNAL_DATA_ABILITY_URI, String.valueOf(id));
            //这里需要"w"写权限
            FileDescriptor fd = helper.openFile(uri, "w");
            ImagePacker imagePacker = ImagePacker.create();
            ImagePacker.PackingOptions packingOptions = new ImagePacker.PackingOptions();
            OutputStream outputStream = new FileOutputStream(fd);
            packingOptions.format = "image/jpeg";
            packingOptions.quality = 90;
            boolean result = imagePacker.initializePacking(outputStream, packingOptions);
            if (result) {
                result = imagePacker.addImage(pixelMap);
                if (result) {
                    long dataSize = imagePacker.finalizePacking();
                }
            }
            outputStream.flush();
            outputStream.close();
            valuesBucket.clear();
            //解除独占
            valuesBucket.putInteger("is_pending", 0);
            helper.update(uri, valuesBucket, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
