package com.kuili.takephoto.slice;

import com.kuili.takephoto.ResourceTable;
import com.kuili.takephoto.utils.DistributeFileUtil;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Image;

public class ImageAbilitySlice extends AbilitySlice {

    private Image image;

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_image);
        initView(intent);
    }

    private void initView(Intent intent) {
        if (findComponentById(ResourceTable.Id_image) instanceof Image) {
            image = (Image) findComponentById(ResourceTable.Id_image);
        }
        setDisImage(intent);
    }

    /**
     * 从分布式文件服务中读取图片并展示
     *
     * @param intent intent
     */
    private void setDisImage(Intent intent) {
        String filePath = intent.getStringParam("filePath");
        if (filePath != null && !filePath.isEmpty()) {
            image.setPixelMap(DistributeFileUtil.readToDistributedDir(this, filePath));
        }
    }
}
