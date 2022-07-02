package org.btik.server.video.device;


import org.btik.server.VideoServer;
import org.btik.server.video.AsyncTaskExecutor;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发送帧设备接入通道
 */
public class BioDeviceChannel extends Thread  {


    private boolean runFlag = true;

    private final byte[] countLock = new byte[0];

    private int clientsLimit;

    private int streamPort;

    private AsyncTaskExecutor asyncTaskExecutor;

    private Set<Socket> clients;

    private Map<Socket, FrameReceiver> receiverMap;

    private VideoServer videoServer;

    public BioDeviceChannel() {
        super("bioDevChannel");
    }

    @Override
    public synchronized void start() {
        clients = Collections.newSetFromMap(new ConcurrentHashMap<>());
        receiverMap = new ConcurrentHashMap<>();
        super.start();
        System.out.println("bio Device Channel started");
    }


    public void shutDown(String msg) {
        runFlag = false;
    }


    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(streamPort)) {
            while (runFlag) {
                Socket cam = serverSocket.accept();
                synchronized (countLock) {
                    onNewStreamOpen(cam);
                    if (clients.size() >= clientsLimit) {
                        try {
                            countLock.wait();
                        } catch (InterruptedException e) {
                            System.out.println("break on wait:" + e.getMessage());
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(" start server failed:" + e.getMessage());
        }
    }

    private void close(Socket socket) {
        asyncTaskExecutor.execute(() -> {
            System.out.println("close:" + socket);
            try {
                socket.close();
                synchronized (countLock) {
                    clients.remove(socket);

                    FrameReceiver remove = receiverMap.remove(socket);
                    if (null != remove) {
                        remove.shutDown("connect close");
                    }

                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        });
    }

    private void onNewStreamOpen(Socket socket) {
        try {
            FrameReceiver frameReceiver = new FrameReceiver(videoServer, socket);
            receiverMap.put(socket, frameReceiver);
            frameReceiver.start();
            clients.add(socket);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

    }

    public void setStreamPort(int streamPort) {
        this.streamPort = streamPort;
    }

    public int getStreamPort() {
        return streamPort;
    }


    public void setAsyncTaskExecutor(AsyncTaskExecutor asyncTaskExecutor) {
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    public void setClientsLimit(int clientsLimit) {
        this.clientsLimit = clientsLimit;
    }

    public void setVideoServer(VideoServer videoServer) {
        this.videoServer = videoServer;
    }
}
