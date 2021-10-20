package com.kuili.takephoto;

import com.kuili.takephoto.slice.ImageAbilitySlice;
import ohos.aafwk.ability.Ability;
import ohos.aafwk.content.Intent;

public class ImageAbility extends Ability {
    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setMainRoute(ImageAbilitySlice.class.getName());
    }
}
