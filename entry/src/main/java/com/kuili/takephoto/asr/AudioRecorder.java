package com.kuili.takephoto.asr;

import ohos.app.Context;
import ohos.app.dispatcher.task.TaskPriority;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import ohos.media.audio.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class AudioRecorder {

    private static final String TAG = AudioRecorder.class.getName();
    private static final HiLogLabel LABEL_LOG = new HiLogLabel(3, 0xD000F00, TAG);

    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 1024;

    private Context abilityContext;

    private AudioManager audioManager = new AudioManager();

    private AudioCapturer audioCapturer;

    private boolean isRecording = false;

    AudioRecorder(Context context) {
        abilityContext = context;
    }

    private AudioCapturerCallback callback = new AudioCapturerCallback() {
        @Override
        public void onCapturerConfigChanged(List<AudioCapturerConfig> configs) {
            HiLog.info(LABEL_LOG, "%{public}s", "on capturer config changed");
        }
    };

    public void initRecord() {
        audioManager.registerAudioCapturerCallback(callback);
        AudioDeviceDescriptor[] devices = AudioManager.getDevices(AudioDeviceDescriptor.DeviceFlag.INPUT_DEVICES_FLAG);
        AudioDeviceDescriptor currentAudioType = devices[0];
        AudioCapturerInfo.AudioInputSource source = AudioCapturerInfo.AudioInputSource.AUDIO_INPUT_SOURCE_MIC;
        AudioStreamInfo audioStreamInfo = new AudioStreamInfo.Builder().audioStreamFlag(
                AudioStreamInfo.AudioStreamFlag.AUDIO_STREAM_FLAG_AUDIBILITY_ENFORCED)
                .encodingFormat(AudioStreamInfo.EncodingFormat.ENCODING_PCM_16BIT)
                .channelMask(AudioStreamInfo.ChannelMask.CHANNEL_IN_MONO)
                .streamUsage(AudioStreamInfo.StreamUsage.STREAM_USAGE_MEDIA)
                .sampleRate(SAMPLE_RATE)
                .build();
        AudioCapturerInfo audioCapturerInfo = new AudioCapturerInfo.Builder().audioStreamInfo(audioStreamInfo)
                .audioInputSource(source)
                .build();
        audioCapturer = new AudioCapturer(audioCapturerInfo, currentAudioType);
    }

    public void startRecord() {
        if (isRecording && audioCapturer != null) {
            stopRecord();
            return;
        }
        record();
    }

    public void stopRecord() {
        if (audioCapturer.stop()) {
            isRecording = false;
        }
    }

    private void record() {
        if (audioCapturer.start()) {
            isRecording = true;
//            runRecord();
        }
    }

    private void runRecord() {
        abilityContext.getGlobalTaskDispatcher(TaskPriority.DEFAULT).asyncDispatch(() -> {
            byte[] bytes = new byte[BUFFER_SIZE];
            while (audioCapturer.read(bytes, 0, bytes.length) != -1) {
                HiLog.info(LABEL_LOG, "读取到数据长度为:" + bytes.length);
            }
        });
    }

    public int read(byte[] buffer) {
        return audioCapturer.read(buffer, 0, buffer.length);
    }

    public void savePcmFile(String pathName, byte[] bytes, boolean first) {
        File file = new File(pathName);
        if (first) {
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                outputStream.write(bytes);
                outputStream.flush();
            } catch (IOException exception) {
                HiLog.error(LABEL_LOG, "%{public}s", "record exception");
            }
        } else {
            try (FileOutputStream outputStream = new FileOutputStream(file, true)) {
                outputStream.write(bytes);
                outputStream.flush();
            } catch (IOException exception) {
                HiLog.error(LABEL_LOG, "%{public}s", "record exception");
            }
        }

    }
}
