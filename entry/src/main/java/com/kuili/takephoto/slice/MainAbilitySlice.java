package com.kuili.takephoto.slice;

import com.kuili.takephoto.ImageAbility;
import com.kuili.takephoto.ResourceTable;
import com.kuili.takephoto.asr.AsrClient;
import com.kuili.takephoto.utils.*;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.*;
import ohos.agp.components.surfaceprovider.SurfaceProvider;
import ohos.agp.graphics.Surface;
import ohos.agp.graphics.SurfaceOps;
import ohos.app.Environment;
import ohos.eventhandler.EventHandler;
import ohos.eventhandler.EventRunner;
import ohos.media.camera.CameraKit;
import ohos.media.camera.device.Camera;
import ohos.media.camera.device.CameraConfig;
import ohos.media.camera.device.CameraStateCallback;
import ohos.media.camera.device.FrameConfig;
import ohos.media.image.ImageReceiver;
import ohos.media.image.PixelMap;
import ohos.media.image.common.ImageFormat;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ohos.media.camera.device.Camera.FrameConfigType.FRAME_CONFIG_PICTURE;
import static ohos.media.camera.device.Camera.FrameConfigType.FRAME_CONFIG_PREVIEW;

public class MainAbilitySlice extends AbilitySlice implements PermissionBridge.OnPermissionStateListener {

    static final String TAG = "MainAbilitySlice";

    private static final int SCREEN_WIDTH = 1080;
    private static final int SCREEN_HEIGHT = 2340;
    private static final int IMAGE_RCV_CAPACITY = 9;

    private String cameraId;

    private Surface previewSurface;
    private SurfaceProvider surfaceProvider;

    private Image takePictureBtn;
    private ImageReceiver imageReceiver;

    private Camera cameraDevice;
    private EventHandler creamEventHandler;

    private String fileName;
    private File targetFile;

    private static final List<String> COMMAND_STRING = new ArrayList<>();
    static {
        COMMAND_STRING.add("拍照");
        COMMAND_STRING.add("茄子");
    }

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);

        new PermissionBridge().setOnPermissionStateListener(this);
    }

    @Override
    public void onPermissionGranted() {
        LogUtil.info(TAG, "权限都允许了，开始相机预览初始化");
        initSurface();
        initControlComponents();
        initAsrClient();
    }

    @Override
    public void onPermissionDenied() {
        LogUtil.info(TAG, "权限被拒绝了，完犊子了我要被干掉了");
    }

    private void initSurface() {
        // 1. 创建用于存放相机预览画面的surfaceProvider
        surfaceProvider = new SurfaceProvider(this);
        DirectionalLayout.LayoutConfig params =
                new DirectionalLayout.LayoutConfig(
                        ComponentContainer.LayoutConfig.MATCH_PARENT, ComponentContainer.LayoutConfig.MATCH_PARENT);

        surfaceProvider.setLayoutConfig(params);
        surfaceProvider.pinToZTop(true);

        // 2. 设置预览回调，当surfaceProvider准备好时调用openCamera
        surfaceProvider.getSurfaceOps().get().addCallback(new SurfaceOps.Callback() {
            @Override
            public void surfaceCreated(SurfaceOps callbackSurfaceOps) {
                if (callbackSurfaceOps != null) {
                    callbackSurfaceOps.setFixedSize(surfaceProvider.getHeight(), surfaceProvider.getWidth());
                }
                openCamera();
            }

            @Override
            public void surfaceChanged(SurfaceOps callbackSurfaceOps, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceOps callbackSurfaceOps) {
            }
        });

        // 3. 把存放预览照片的surfaceProvider放入surfaceContainer布局
        Component surfaceContainer = findComponentById(ResourceTable.Id_surface_container);
        if (surfaceContainer instanceof ComponentContainer) {
            ((ComponentContainer) surfaceContainer).addComponent(surfaceProvider);
        }
    }

    private void initControlComponents() {
        if (findComponentById(ResourceTable.Id_tack_photo_btn) instanceof Image) {
            takePictureBtn = (Image) findComponentById(ResourceTable.Id_tack_photo_btn);
        }
        takePictureBtn.setClickedListener(this::takePicture);
    }

    private void takePicture(Component component) {
        LogUtil.info(TAG, "开始拍照");
        if (!takePictureBtn.isEnabled()) {
            LogUtil.info(TAG, "拍照功能未使能");
            return;
        }
        if (cameraDevice == null || imageReceiver == null) {
            LogUtil.info(TAG, "相机设备或者图片接受器为空");
            return;
        }

        // 拍摄单张照片
        FrameConfig.Builder framePictureConfigBuilder = cameraDevice.getFrameConfigBuilder(FRAME_CONFIG_PICTURE);
        framePictureConfigBuilder.addSurface(imageReceiver.getRecevingSurface());
        FrameConfig pictureFrameConfig = framePictureConfigBuilder.build();
        cameraDevice.triggerSingleCapture(pictureFrameConfig);
    }

    private void initAsrClient() {
        AsrClient.start(this, result -> {
            LogUtil.info(TAG, result);

            // 查找识别结果result字符串中是否包含识别关键字COMMAND_STRING
            boolean command = false;
            for(int i = 0; i < COMMAND_STRING.size(); i++) {
                if (result.contains(COMMAND_STRING.get(i))) {
                    command = true;
                    break;
                }
            }
            if (command) {
                // 匹配到COMMAND_STRING中的关键字，开始执行拍照功能
                takePicture(new Component(getContext()));
            }
        });
    }

    private void openCamera() {
        imageReceiver = ImageReceiver.create(SCREEN_WIDTH, SCREEN_HEIGHT, ImageFormat.JPEG, IMAGE_RCV_CAPACITY);
        imageReceiver.setImageArrivalListener(this::saveImage);

        // 创建camereKit对象
        CameraKit cameraKit = CameraKit.getInstance(getApplicationContext());
        if (cameraKit == null) {
            LogUtil.error(TAG, "CameraKit对象打开失败");
            return ;
        }

        // 获取逻辑摄像头
        try {
            // 获取当前设备的逻辑相机列表
            String[] cameraIds = cameraKit.getCameraIds();
            if (cameraIds.length <= 0) {
                LogUtil.error(TAG, "找不到摄像头");
            }
            // 选择第一个逻辑摄像头
            cameraId = cameraIds[0];
        } catch (IllegalStateException e) {
            // 处理异常
            LogUtil.error(TAG, e.getMessage());
        }

        // 创建摄像头的回调方法，以便cameraKit对象创建成功后，可以完成相关的创建配置操作
        CameraStateCallback cameraStateCallback = new CameraStateCallback() {
            @Override
            public void onCreated(Camera camera) {
                previewSurface = surfaceProvider.getSurfaceOps().get().getSurface();
                if (previewSurface == null) {
                    LogUtil.info(TAG, "create camera filed, preview surface is null");
                    return;
                }

                try {
                    Thread.sleep(200);
                } catch (InterruptedException exception) {
                    LogUtil.info(TAG, "Waiting to be interrupted");
                }

                CameraConfig.Builder cameraConfigBuilder = camera.getCameraConfigBuilder();
                cameraConfigBuilder.addSurface(previewSurface);
                cameraConfigBuilder.addSurface(imageReceiver.getRecevingSurface());
                camera.configure(cameraConfigBuilder.build());
                cameraDevice = camera;
            }

            @Override
            public void onConfigured(Camera camera) {
                FrameConfig.Builder framePreviewConfigBuilder = camera.getFrameConfigBuilder(FRAME_CONFIG_PREVIEW);
                framePreviewConfigBuilder.addSurface(previewSurface);
                try {
                    // 启动循环帧捕获
                    camera.triggerLoopingCapture(framePreviewConfigBuilder.build());
                } catch (IllegalArgumentException e) {
                    LogUtil.error(TAG, "Argument Exception");
                } catch (IllegalStateException e) {
                    LogUtil.error(TAG, "State Exception");
                }
            }
        };
        // 创建摄像头内部事件处理Handler
        creamEventHandler = new EventHandler(EventRunner.create("CameraCb"));
        // 一切准备就绪，开始创建camera
        cameraKit.createCamera(cameraId, cameraStateCallback, creamEventHandler);
    }

    private void saveImage(ImageReceiver receiver) {
        fileName = "IMG_" + System.currentTimeMillis() + ".png";
        targetFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName);
        try {
            LogUtil.info(TAG, "filePath is " + targetFile.getCanonicalPath());
        } catch (IOException e) {
            LogUtil.error(TAG, "filePath is error");
        }

        ohos.media.image.Image image = receiver.readNextImage();
        if (image == null) {
            return;
        }
        ohos.media.image.Image.Component component = image.getComponent(ImageFormat.ComponentType.JPEG);
        byte[] bytes = new byte[component.remaining()];
        component.read(bytes);

        try (FileOutputStream output = new FileOutputStream(targetFile)) {
            output.write(bytes);
            output.flush();

            // 把拍摄的照片放到分布式文件服务目录下
            String pathName = DistributeFileUtil.copyPicToDistributedDir(MainAbilitySlice.this, targetFile, fileName);
            // 启动远程和本地的FA，展示拍照结果页面
            if (!pathName.isEmpty()) {
                Map<String, String> params = new HashMap<>();
                params.put("filePath", pathName);
                RemoteFA.startRemoteFA(this, getBundleName(), ImageAbility.class.getName(), params);
                RemoteFA.startLocalFA(this, getBundleName(), ImageAbility.class.getName(), params);
            }
        } catch (IOException e) {
            LogUtil.info(TAG, "IOException, 保存图片失败");
        }

        // 拍摄的照片保存到相册
        PixelMap pixelMap = CameraUtil.getPixelMap(bytes, "", 1);
        GalleryUtil.saveToGallery(this, fileName, pixelMap);
    }

    private void releaseCamera() {
        if (cameraDevice != null) {
            cameraDevice.release();
            cameraDevice = null;
        }

        if (imageReceiver != null) {
            imageReceiver.release();
            imageReceiver = null;
        }

        if (creamEventHandler != null) {
            creamEventHandler.removeAllEvent();
            creamEventHandler = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseCamera();
        AsrClient.stop();
    }

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }
}
