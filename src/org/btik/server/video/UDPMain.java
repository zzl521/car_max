package org.btik.server.video;

import org.btik.server.video.device.UDPDeviceChannel;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class UDPMain {
    static Properties properties;

    static {
        properties = new Properties();
        try {
            properties.load(new FileInputStream("light-video.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 读取配置信息
     *
     * @param key 配置key
     * @param def 获取为空时的默认值
     */
    private static String getProp(String key, String def) {
        Object o = properties.get(key);
        if (o == null) {
            return def;
        }
        return String.valueOf(o);
    }

    public static void main(String[] args) {
        AsyncTaskExecutor asyncTaskExecutor = new AsyncTaskExecutor();
        asyncTaskExecutor.start();

        BioHttpVideoServer bioHttpVideoServer = new BioHttpVideoServer();
        bioHttpVideoServer.setHttpPort(Integer.parseInt(
                getProp("http.port", "8003")));
        bioHttpVideoServer.setAsyncTaskExecutor(asyncTaskExecutor);
        bioHttpVideoServer.start();

        UDPDeviceChannel deviceChannel = new UDPDeviceChannel();
        deviceChannel.setAsyncTaskExecutor(asyncTaskExecutor);
        deviceChannel.setVideoServer(bioHttpVideoServer);
        deviceChannel.setStreamPort(Integer.parseInt(
                getProp("stream.port", "8004")));
        deviceChannel.setBufferPoolSize(Integer.parseInt(getProp("udp.video.buffer.pool.size", "500")));
        deviceChannel.setDispatcherPoolSize(Integer.parseInt(getProp("udp.video.dispatcher.thread.size", "8")));
        deviceChannel.start();
    }
}
