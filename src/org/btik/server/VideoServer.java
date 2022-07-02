package org.btik.server;

import org.btik.server.video.device.FrameBuffer;
import org.btik.server.video.device.FrameReceiver;

/**
 *视频服务
 */
public interface VideoServer {

    /**
     * 字节数组发送给不同客户端
     *
     * @param frame 一帧jpeg
     */
    void sendFrame(FrameBuffer buffer);



}
