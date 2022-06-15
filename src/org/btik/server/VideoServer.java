package org.btik.server;

/**
 *视频服务
 */
public interface VideoServer {

    /**
     * 字节数组发送给不同客户端
     *
     * @param frame 一帧jpeg
     */
    void sendFrame(byte[] frame);



}
