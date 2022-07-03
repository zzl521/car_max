package org.btik.server.video;

import org.btik.server.util.ByteUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频频道
 * 在线的摄像头均有一个频道
 */
public class MJPEGVideoChannel implements VideoChannel, HttpConstant {

    private final AsyncTaskExecutor asyncTaskExecutor;

    /**
     * 暂时没有用，debug时可以分辨属于哪个设备
     */
    private String channelId;

    private final Set<Socket> clients = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final byte[] clientLock = new byte[0];

    public MJPEGVideoChannel(String channelId, AsyncTaskExecutor asyncTaskExecutor) {
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.channelId = channelId;

    }

    @Override
    public void sendFrame(byte[] frame, int len) {
        byte[] lenStrBytes = ByteUtil.toString(len);
        byte[] lenHexStrBytes = ByteUtil.toHexString(len);
        synchronized (clientLock) {
            for (Socket client : clients) {
                try {
                    OutputStream outputStream = client.getOutputStream();
                    sendChunk(_STREAM_BOUNDARY, outputStream);
                    sendChunk(outputStream, _STREAM_PART, lenStrBytes, DOUBLE_LINE);
                    sendChunk(frame, len, lenHexStrBytes, outputStream);
                    outputStream.flush();
                } catch (IOException e) {
                    checkState(client, e);
                }

            }
        }
    }

    /**
     * 加入频道
     */
    public void joinChannel(Socket client) throws IOException {
        System.out.println("open:" + client.getRemoteSocketAddress());
        OutputStream outputStream = client.getOutputStream();
        outputStream.write(STREAM_RESP_HEAD_BYTES);
        outputStream.flush();
        client.setTcpNoDelay(true);
        clients.add(client);
    }

    private void checkState(Socket socket, Exception e) {
        if (socket.isClosed()) {
            disConnect(socket, e);
        }
    }

    void sendChunk(byte[] chunk, OutputStream out) throws IOException {
        int length = chunk.length;
        out.write(NEW_LINE);
        out.write(ByteUtil.toHexString(length));
        out.write(NEW_LINE);
        out.write(chunk);

    }

    void sendChunk(byte[] chunkBuffer, final int len, byte[] lenHexStrBytes, OutputStream out) throws IOException {
        out.write(NEW_LINE);
        out.write(lenHexStrBytes);
        out.write(NEW_LINE);
        out.write(chunkBuffer, 0, len);

    }

    void sendChunk(OutputStream out, byte[]... chunk) throws IOException {
        int length = 0;
        for (byte[] bytes : chunk) {
            length += bytes.length;
        }

        out.write(NEW_LINE);
        out.write(ByteUtil.toHexString(length));
        out.write(NEW_LINE);
        for (byte[] bytes : chunk) {
            out.write(bytes);
        }

    }

    void disConnect(Socket socket, Exception e) {
        asyncTaskExecutor.execute(() -> {
            System.err.println(e.getMessage());
            try {
                System.err.println("close:" + socket.getRemoteSocketAddress());
                socket.close();
            } catch (IOException e0) {
                System.err.println(e.getMessage());
            }
        });

    }

}
