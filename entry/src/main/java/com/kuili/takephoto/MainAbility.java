package com.kuili.takephoto;

import com.kuili.takephoto.slice.MainAbilitySlice;
import com.kuili.takephoto.utils.LogUtil;
import com.kuili.takephoto.utils.PermissionBridge;
import ohos.aafwk.ability.Ability;
import ohos.aafwk.content.Intent;
import ohos.security.SystemPermission;

import java.util.ArrayList;
import java.util.List;

import static com.kuili.takephoto.utils.PermissionBridge.EVENT_PERMISSION_DENIED;
import static com.kuili.takephoto.utils.PermissionBridge.EVENT_PERMISSION_GRANTED;
import static ohos.bundle.IBundleManager.PERMISSION_GRANTED;

public class MainAbility extends Ability {
    static final String TAG = MainAbility.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CODE = 1;

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setMainRoute(MainAbilitySlice.class.getName());

        // 动态申请系统权限
        requestCameraPermission();
    }

    private void requestCameraPermission() {
        // 定义一个数组，存放需要动态申请的权限
        List<String> permissions = new ArrayList<String>();
        permissions.add(SystemPermission.CAMERA);
        permissions.add(SystemPermission.WRITE_USER_STORAGE);
        permissions.add(SystemPermission.READ_USER_STORAGE);
        permissions.add(SystemPermission.DISTRIBUTED_DATASYNC);
        permissions.add(SystemPermission.MICROPHONE);
        permissions.add(SystemPermission.INTERNET);

        // 判断哪些权限之前已经申请过了，通过removeIf把已经申请过的权限从数组中去掉
        permissions.removeIf(
                permission ->
                        verifySelfPermission(permission) == PERMISSION_GRANTED || !canRequestPermission(permission));
        // 如果需要申请的权限不为空，则使用requestPermissionsFromUser进行动态申请，否则发送Granted事件，通知MainAbilitySlice初始化摄像头预览
        if (!permissions.isEmpty()) {
            requestPermissionsFromUser(permissions.toArray(new String[permissions.size()]), PERMISSION_REQUEST_CODE);
        } else {
            PermissionBridge.getHandler().sendEvent(EVENT_PERMISSION_GRANTED);
            LogUtil.info(TAG, "未发现需要授权的权限");
        }
    }

    @Override
    public void onRequestPermissionsFromUserResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            LogUtil.info(TAG, "未知的权限请求码");
            return;
        }
        int i = 0;
        for (int grantResult : grantResults) {
            if (grantResult != PERMISSION_GRANTED) {
                PermissionBridge.getHandler().sendEvent(EVENT_PERMISSION_DENIED);
                terminateAbility();
                LogUtil.info(TAG, permissions[i] + "的权限请求被拒绝");
                LogUtil.info(TAG, String.valueOf(grantResult));
                return;
            }
            i++;
        }
        LogUtil.info(TAG, "权限请求成功");
        PermissionBridge.getHandler().sendEvent(EVENT_PERMISSION_GRANTED);
    }
}
