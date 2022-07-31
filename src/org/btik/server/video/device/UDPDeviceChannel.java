package org.btik.server.video.device;

import org.btik.server.VideoServer;
import org.btik.server.util.NamePrefixThreadFactory;
import org.btik.server.video.AsyncTaskExecutor;
import org.btik.server.video.VideoChannel;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.*;


/**
 * 发送帧设备接入通道
 */
public class UDPDeviceChannel extends Thread {
    /**
     * 接收图片缓冲区大小，<br>
     * 与TCP不同的是，若图片大于当前帧大小,会截断则无法得到完整图片，默认40KB
     */
    private static final int RECEIVE_BUFFER_SIZE = 40960;

    /**
     * 初始缓存区池大小，本身会自动扩容，随着设备增多可以设置合理值
     */
    private int bufferPoolSize = 500;

    private static final int SN_LEN = 12;
    private volatile boolean runFlag = true;

    /**
     * 帧通道端口号
     */
    private int streamPort;

    private VideoServer videoServer;

    private AsyncTaskExecutor asyncTaskExecutor;

    private ExecutorService executorService;


    private FrameDispatcher[] frameDispatchers;

    /**
     * 帧分发线程数量，随着设备增多可以适当增加
     */
    private int dispatcherPoolSize = 8;

    public void setStreamPort(int streamPort) {
        this.streamPort = streamPort;
    }

    public void setVideoServer(VideoServer videoServer) {
        this.videoServer = videoServer;
    }

    public void setAsyncTaskExecutor(AsyncTaskExecutor asyncTaskExecutor) {
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    /**
     * 可选的输入值  1 2 4 8 16 32 64 128 256几个数字，根据cpu核数和设备的数量选择合适的值
     * ，输入其它值也会被映射到以上值，如果只有一个摄像头设备那就一个足够，线程数太多而cpu核数过少，
     * 反而因为线程不断切换使得效率更低
     */
    public void setDispatcherPoolSize(int dispatcherPoolSize) {
        int maximumCapacity = 256;
        int n = -1 >>> Integer.numberOfLeadingZeros(dispatcherPoolSize - 1);
        this.dispatcherPoolSize = (n < 0) ? 1 : (n >= maximumCapacity) ? maximumCapacity : n + 1;
    }

    public void setBufferPoolSize(int bufferPoolSize) {
        this.bufferPoolSize = bufferPoolSize;
    }

    /**
     * 帧缓冲池，避免反复new帧缓冲区
     */
    private final ConcurrentLinkedQueue<FrameBuffer> frameBufferPool = new ConcurrentLinkedQueue<>();

    private final ConcurrentHashMap<Long, VideoChannel> videoChannelMap = new ConcurrentHashMap<>();


    @Override
    public synchronized void start() {
        System.out.println("init buffer pool");
        for (int i = 0; i < bufferPoolSize; i++) {
            frameBufferPool.add(new FrameBuffer(new byte[RECEIVE_BUFFER_SIZE]));
        }

        super.start();
        System.out.println("start dispatchers");
        frameDispatchers = new FrameDispatcher[dispatcherPoolSize];
        executorService = new ThreadPoolExecutor(dispatcherPoolSize, dispatcherPoolSize,
                0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new NamePrefixThreadFactory("frameDispatcher"));
        for (int i = 0; i < dispatcherPoolSize; i++) {
            FrameDispatcher msgDispatcher = new FrameDispatcher();
            frameDispatchers[i] = msgDispatcher;
            executorService.submit(msgDispatcher);
        }

        System.out.println("udp channel loaded");
    }

    @Override
    public void run() {

        try (DatagramSocket serverSocket = new DatagramSocket(streamPort)) {
            FrameBuffer frameBuffer = getFrameBuffer();
            DatagramPacket datagramPacket = new DatagramPacket(frameBuffer.data, 0, frameBuffer.data.length);
            while (runFlag) {
                serverSocket.receive(datagramPacket);
                InetAddress address = datagramPacket.getAddress();
                frameBuffer.address = (long) address.hashCode() << 16 | datagramPacket.getPort();
                frameBuffer.size = datagramPacket.getLength();
                frameDispatchers[(int) (frameBuffer.address & dispatcherPoolSize - 1)].messages.add(frameBuffer);
                // 切换缓冲区
                frameBuffer = getFrameBuffer();
            }
        } catch (IOException e) {
            System.out.println(" start server failed:" + e.getMessage());
        }
    }

    public void shutDown() {
        runFlag = false;
        // 无消息导致阻塞时，没有读到flag,帮助退出阻塞
        for (FrameDispatcher frameDispatcher : frameDispatchers) {
            frameDispatcher.messages.add(new FrameBuffer(new byte[0]));
        }
        // 线程池核心线程也需要停止
        executorService.shutdown();
    }

    private FrameBuffer getFrameBuffer() {
        FrameBuffer buffer = frameBufferPool.poll();
        if (buffer == null) {
            // 自动扩容
            buffer = new FrameBuffer(new byte[RECEIVE_BUFFER_SIZE]);
        }
        return buffer;
    }

    /**
     * 单帧图片
     */
    static class FrameBuffer {
        // 2 + 4 + 2字节 2 字节的0 4字节ip 2字节端口
        long address;

        byte[] data;

        int size;

        public FrameBuffer(byte[] data) {
            this.data = data;
        }
    }

    class FrameDispatcher implements Runnable {
        LinkedBlockingQueue<FrameBuffer> messages = new LinkedBlockingQueue<>();

        @Override
        public void run() {

            try {
                while (runFlag) {
                    FrameBuffer frame = messages.take();
                    try {
                        byte[] data = frame.data;
                        int length = data.length;
                        if (length == SN_LEN) {
                            asyncTaskExecutor.execute(() -> onNewStreamOpen(frame));
                            continue;
                        }
                        long address = frame.address;
                        VideoChannel channel = videoChannelMap.get(address);
                        if (channel != null) {
                            channel.sendFrame(data, length);
                        }
                    } finally {
                        // 归还到池里
                        frameBufferPool.add(frame);
                    }

                }
            } catch (InterruptedException e) {
                System.out.println("exit by:" + e);
            }
        }
    }

    private void onNewStreamOpen(FrameBuffer frame) {
        byte[] sn = new byte[SN_LEN + 1];
        System.arraycopy(frame.data, 0, sn, 1, SN_LEN);
        VideoChannel channel = videoServer.createChannel(sn);
        videoChannelMap.put(frame.address, channel);
        // 归还单帧缓冲区
        frameBufferPool.add(frame);
    }

}
