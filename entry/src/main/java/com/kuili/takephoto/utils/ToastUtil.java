package com.kuili.takephoto.utils;

import ohos.agp.utils.LayoutAlignment;
import ohos.agp.window.dialog.ToastDialog;
import ohos.app.Context;

public class ToastUtil {

    private ToastUtil() {
    }

    public static void showTip(Context context, String msg) {
        context.getUITaskDispatcher()
                .delayDispatch(
                        new Runnable() {
                            @Override
                            public void run() {
                                ToastDialog toastDialog = new ToastDialog(context);
                                toastDialog.setAutoClosable(false);
                                toastDialog.setAlignment(LayoutAlignment.CENTER);
                                toastDialog.setText(msg);
                                toastDialog.show();
                            }
                        },0);
    }
}
