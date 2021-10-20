package com.kuili.takephoto.asr;

import ohos.app.Context;

public class AsrClient {

    private AsrClient() {}

    public static void start(Context context, AsrClientCallback callback) {
        try {
            RTASRClient.rtasrStart(context, callback);
        } catch (Exception e) {

        }
    }

    public static void stop() {
        RTASRClient.stop();
    }

    public interface AsrClientCallback {
        void onResult(String result);
    }
}
