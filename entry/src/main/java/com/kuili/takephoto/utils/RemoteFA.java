package com.kuili.takephoto.utils;

import ohos.aafwk.content.Intent;
import ohos.aafwk.content.Operation;
import ohos.app.Context;
import ohos.bundle.AbilityInfo;
import ohos.distributedschedule.interwork.DeviceInfo;
import ohos.distributedschedule.interwork.DeviceManager;
import ohos.rpc.RemoteException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class RemoteFA {

    private static final String TAG = "RemoteFA";

    private RemoteFA() {}

    public static void startLocalFA(Context context, String bundleName, String abilityName, Map<String, String> params) {
        Intent intent = new Intent();
        if (params != null && !params.isEmpty()) {
            Iterator<Map.Entry<String, String>> iterator = params.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<String, String> entry = iterator.next();
                intent.setParam(entry.getKey(), entry.getValue());
            }
        }
        Operation operation =
                new Intent.OperationBuilder()
                        .withBundleName(bundleName)
                        .withAbilityName(abilityName)
                        .build();
        intent.setOperation(operation);
        try {
            context.startAbility(intent, 0);
        } catch (Exception e) {
            LogUtil.error(TAG, e.getMessage());
        }
    }

    public static void startRemoteFA(Context context, String bundleName, String abilityName, Map<String, String> params) {
        //处理按钮响应，获取在线设备列表
        List<DeviceInfo> deviceInfoList =
                DeviceManager.getDeviceList(DeviceInfo.FLAG_GET_ONLINE_DEVICE);
        if (deviceInfoList == null || deviceInfoList.size() < 1) {
            LogUtil.info(TAG, "没有发现周围的设备");
            return;
        }
        Intent[] intents = new Intent[deviceInfoList.size()];
        for (int i = 0; i < deviceInfoList.size(); i++) {
            // 远程启动FA
            Intent remoteIntent = new Intent();
            if (params != null && !params.isEmpty()) {
                Iterator<Map.Entry<String, String>> iterator = params.entrySet().iterator();
                while (iterator.hasNext()){
                    Map.Entry<String, String> entry = iterator.next();
                    remoteIntent.setParam(entry.getKey(), entry.getValue());
                }
            }
            // 指定待启动FA的bundleName和abilityName
            // 例如：BUNDLE_NAME = "com.kuili.remotefa"
            //       ABILITY_NAME = "com.kuili.remotefa.MainAbility"
            // 设置分布式标记，表明当前涉及分布式能力
            Operation operation = new Intent.OperationBuilder().withDeviceId(deviceInfoList.get(i).getDeviceId())
                    .withBundleName(bundleName)
                    .withAbilityName(abilityName)
                    .withFlags(Intent.FLAG_ABILITYSLICE_MULTI_DEVICE)
                    .build();
            remoteIntent.setOperation(operation);
            intents[i] = remoteIntent;
//            try {
                // 目标设备是否包含指定FA
//                List<AbilityInfo> abilityInfoList = context.getBundleManager().queryAbilityByIntent(remoteIntent, 0, 0);
//                if (abilityInfoList != null && !abilityInfoList.isEmpty()) {
//                    context.startAbility(remoteIntent, 0);
//                }
//            } catch (RemoteException e) {
//                // 处理异常
//                LogUtil.error(TAG, e.getMessage());
//            }
        }
        context.startAbilities(intents);
    }
}
