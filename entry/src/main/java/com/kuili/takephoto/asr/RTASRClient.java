package com.kuili.takephoto.asr;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kuili.takephoto.utils.LogUtil;
import ohos.app.Context;
import ohos.app.dispatcher.task.TaskPriority;
import org.java_websocket.WebSocket.READYSTATE;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * 实时转写
 *
 */
public class RTASRClient {

    private static final String TAG = "RTASRClient";

    // appid
    private static final String APPID = "xxxxxxxx";

    // appid对应的secret_key
    private static final String SECRET_KEY = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    // 请求地址
    private static final String HOST = "rtasr.xfyun.cn/v1/ws";

    private static final String BASE_URL = "wss://" + HOST;

    private static final String ORIGIN = "https://" + HOST;

    // 每次发送的数据大小 1280 字节
    private static final int CHUNCKED_SIZE = 1280;

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS");

    private static Context AbilityContext;

    // 录音器
    private static AudioRecorder audioRecorder;
    // 识别回调
    private static AsrClient.AsrClientCallback asrCallback;

    private static boolean running = false;

    public static void rtasrStart(Context context, AsrClient.AsrClientCallback callback) throws Exception {
        context.getGlobalTaskDispatcher(TaskPriority.DEFAULT).asyncDispatch(() -> {
            try {
                AbilityContext = context;
                asrCallback = callback;

                // 初始化录音
                audioRecorder = new AudioRecorder(AbilityContext);
                audioRecorder.initRecord();
                audioRecorder.startRecord();

                running = true;
                while (running) {
                    URI url = new URI(BASE_URL + getHandShakeParams(APPID, SECRET_KEY));
                    DraftWithOrigin draft = new DraftWithOrigin(ORIGIN);
                    CountDownLatch handshakeSuccess = new CountDownLatch(1);
                    CountDownLatch connectClose = new CountDownLatch(1);
                    MyWebSocketClient client = new MyWebSocketClient(url, draft, handshakeSuccess, connectClose);

                    client.connect();

                    while (!client.getReadyState().equals(READYSTATE.OPEN)) {
                        LogUtil.info(TAG, getCurrentTimeStr() + "\t连接中");
                        Thread.sleep(1000);
                    }

                    // 等待握手成功
                    handshakeSuccess.await();
                    LogUtil.info(TAG, sdf.format(new Date()) + " 开始发送音频数据");
                    // 发送音频
                    byte[] bytes = new byte[CHUNCKED_SIZE];
                    int len = -1;
                    while ((len = audioRecorder.read(bytes)) != -1 && running) {
                        if (len < CHUNCKED_SIZE) {
                            send(client, bytes = Arrays.copyOfRange(bytes, 0, len));
                            break;
                        }
                        send(client, bytes);
                    }

                    // 发送结束标识
                    send(client,"{\"end\": true}".getBytes());
                    LogUtil.info(TAG, "发送结束标识完成");

                    // 等待连接关闭
                    connectClose.await();
                    break;
                }
            } catch (InterruptedException e) {
                LogUtil.error(TAG, e.getMessage());
            } catch (URISyntaxException e) {
                LogUtil.error(TAG, e.getMessage());
            }
        });
    }

    public static void stop() {
        running = false;
    }

    // 生成握手参数
    public static String getHandShakeParams(String appId, String secretKey) {
        String ts = System.currentTimeMillis()/1000 + "";
        String signa = "";
        try {
            signa = EncryptUtil.HmacSHA1Encrypt(EncryptUtil.MD5(appId + ts), secretKey);
            return "?appid=" + appId + "&ts=" + ts + "&signa=" + URLEncoder.encode(signa, "UTF-8");
        } catch (Exception e) {
            LogUtil.error(TAG, getCurrentTimeStr() + e.getMessage());
        }

        return "";
    }

    public static void send(WebSocketClient client, byte[] bytes) {
        if (client.isClosed()) {
            throw new RuntimeException("client connect closed!");
        }

        client.send(bytes);
    }

    public static String getCurrentTimeStr() {
        return sdf.format(new Date());
    }

    public static class MyWebSocketClient extends WebSocketClient {

        private CountDownLatch handshakeSuccess;
        private CountDownLatch connectClose;

        public MyWebSocketClient(URI serverUri, Draft protocolDraft, CountDownLatch handshakeSuccess, CountDownLatch connectClose) {
            super(serverUri, protocolDraft);
            this.handshakeSuccess = handshakeSuccess;
            this.connectClose = connectClose;
            if(serverUri.toString().contains("wss")){
                trustAllHosts(this);
            }
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            LogUtil.info(TAG, getCurrentTimeStr() + "\t连接建立成功！");
        }

        @Override
        public void onMessage(String msg) {
            JSONObject msgObj = JSON.parseObject(msg);
            String action = msgObj.getString("action");
            if (Objects.equals("started", action)) {
                // 握手成功
                LogUtil.info(TAG, getCurrentTimeStr() + "\t握手成功！sid: " + msgObj.getString("sid"));
                handshakeSuccess.countDown();
            } else if (Objects.equals("result", action)) {
                // 转写结果
                asrCallback.onResult(getContent(msgObj.getString("data")));
//                LogUtil.info(TAG, getCurrentTimeStr() + "\tresult: " + getContent(msgObj.getString("data")));
            } else if (Objects.equals("error", action)) {
                // 连接发生错误
                LogUtil.info(TAG, "Error: " + msg);
            }
        }

        @Override
        public void onError(Exception e) {
            LogUtil.info(TAG, getCurrentTimeStr() + "\t连接发生错误：" + e.getMessage() + ", " + new Date());
        }

        @Override
        public void onClose(int arg0, String arg1, boolean arg2) {
            LogUtil.info(TAG, getCurrentTimeStr() + "\t链接关闭");
            connectClose.countDown();
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                LogUtil.info(TAG, getCurrentTimeStr() + "\t服务端返回：" + new String(bytes.array(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LogUtil.error(TAG, getCurrentTimeStr() + e.getMessage());
            }
        }

        public void trustAllHosts(MyWebSocketClient appClient) {
            LogUtil.info(TAG, "wss");
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }

                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }
            }};

            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                appClient.setSocket(sc.getSocketFactory().createSocket());
            } catch (Exception e) {
                LogUtil.error(TAG, getCurrentTimeStr() + e.getMessage());
            }
        }
    }

    // 把转写结果解析为句子
    public static String getContent(String message) {
        StringBuffer resultBuilder = new StringBuffer();
        try {
            JSONObject messageObj = JSON.parseObject(message);
            JSONObject cn = messageObj.getJSONObject("cn");
            JSONObject st = cn.getJSONObject("st");
            JSONArray rtArr = st.getJSONArray("rt");
            for (int i = 0; i < rtArr.size(); i++) {
                JSONObject rtArrObj = rtArr.getJSONObject(i);
                JSONArray wsArr = rtArrObj.getJSONArray("ws");
                for (int j = 0; j < wsArr.size(); j++) {
                    JSONObject wsArrObj = wsArr.getJSONObject(j);
                    JSONArray cwArr = wsArrObj.getJSONArray("cw");
                    for (int k = 0; k < cwArr.size(); k++) {
                        JSONObject cwArrObj = cwArr.getJSONObject(k);
                        String wStr = cwArrObj.getString("w");
                        resultBuilder.append(wStr);
                    }
                }
            } 
        } catch (Exception e) {
            LogUtil.error(TAG, getCurrentTimeStr() + e.getMessage());
            return message;
        }

        return resultBuilder.toString();
    }
}
