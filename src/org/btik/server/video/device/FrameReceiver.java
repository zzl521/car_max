package org.btik.server.video.device;


import org.btik.server.VideoServer;


import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * 帧接收器
 */
public class FrameReceiver extends Thread {
    private volatile boolean runFlag = true;

    private final VideoServer videoServer;

    private final Socket socket;

    private final InputStream in;

    private final FrameSplit frameSplit = new FrameSplit();

    private final FrameBuffer frameBuffer = new FrameBuffer();

    /**
     * 单个摄像头socket 接收图片缓冲区大小
     */
    private static final int RECEIVE_BUFFER_SIZE = 40960;

    private byte[] preFrameBuffer = new byte[RECEIVE_BUFFER_SIZE];

    public FrameReceiver(VideoServer videoServer, Socket socket) throws IOException {
        this.videoServer = videoServer;
        this.socket = socket;
        this.in = socket.getInputStream();
        SocketAddress remoteSocketAddress = socket.getRemoteSocketAddress();
        socket.setReceiveBufferSize(RECEIVE_BUFFER_SIZE);
        setName("frameReceiver" + remoteSocketAddress);
        frameSplit.start();

    }


    @Override
    public void run() {

        while (runFlag) {
            try {
                int available = in.available();
                if (available > 0) {
                    if (available > preFrameBuffer.length) {
                        preFrameBuffer = new byte[available];
                    }
                    int read = in.read(preFrameBuffer);
                    if (read == -1) {
                        shutDown("eof");
                    }
                    frameBuffer.write(preFrameBuffer, 0, read);
                } else {
                    synchronized (in) {
                        in.wait(10);
                    }
                }
            } catch (IOException e) {
                String message = e.getMessage();
                System.err.println(message);
                if ("Connection reset".equals(message)) {
                    shutDown(message);
                }
            } catch (InterruptedException e) {
                System.err.println("wait by break");
                shutDown(e.getMessage());
            }
        }

    }


    public void shutDown(String msg) {
        System.err.println("shutdown on:" + msg);
        runFlag = false;
        synchronized (frameBuffer) {
            frameBuffer.notify();
        }
    }

    @Override
    public synchronized void start() {
        super.start();
        System.out.println("start " + socket.getRemoteSocketAddress());
    }

    class FrameSplit extends Thread {

        public FrameSplit() {
            setName("FrameSplit");
        }

        @Override
        public void run() {
            while (runFlag) {
                synchronized (frameBuffer) {
                    if (frameBuffer.hasFrame()) {
                        videoServer.sendFrame(frameBuffer);
                    } else {
                        try {
                            frameBuffer.wait();
                        } catch (InterruptedException e) {
                            System.err.println("wait by break");
                            shutDown(e.getMessage());
                        }
                    }
                }
            }

        }
    }
}
