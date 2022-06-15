package org.btik.server.video;

import org.btik.server.VideoServer;
import org.btik.server.util.ByteUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/***
 * 以http mjpeg 合成视频流对VideoServer的实现
 *
 * */
public class BioHttpVideoServer extends Thread implements VideoServer, HttpConstant {
    private boolean runFlag = true;

    private static final HashSet<Socket> clients = new HashSet<>();

    private final ExecutorService executorService = Executors.newFixedThreadPool(3, r -> new Thread(r, "client" + System.currentTimeMillis()));

    private AsyncTaskExecutor asyncTaskExecutor;

    private int httpPort;

    private final byte[] clientLock = new byte[0];

    public void setAsyncTaskExecutor(AsyncTaskExecutor asyncTaskExecutor) {
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    @Override
    public synchronized void start() {
        super.start();
        System.out.println("bio video server started");
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(httpPort)) {
            while (runFlag) {
                Socket client = serverSocket.accept();
                InputStream inputStream = client.getInputStream();
                client.setSoTimeout(300);
                byte[] bytes = new byte[URI_LEN];
                try {
                    // 判断EOF
                    if (inputStream.read(bytes) < 0) {
                        asyncTaskExecutor.execute(() -> do404(client));
                        return;
                    }
                    // 判断uri
                    if (!Arrays.equals(bytes, uri)) {
                        asyncTaskExecutor.execute(() -> do404(client));
                        return;
                    }
                    executorService.submit(() -> doStreamOpen(client));
                } catch (IOException e) {
                    disConnect(client, e);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void doStreamOpen(Socket client) {
        try {
            System.out.println("open:" + client.getRemoteSocketAddress());
            OutputStream outputStream = client.getOutputStream();
            outputStream.write(STREAM_RESP_HEAD_BYTES);
            outputStream.flush();
            client.setTcpNoDelay(true);
            clients.add(client);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void do404(Socket client) {
        try {
            client.getOutputStream().write(NOT_FOUND);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            disConnect(client, new Exception("404"));
        }

    }

    void disConnect(Socket socket, Exception e) {
        asyncTaskExecutor.execute(() -> {
            System.err.println(e.getMessage());
            synchronized (clientLock) {
                clients.remove(socket);
            }
            try {
                System.err.println("close:" + socket.getRemoteSocketAddress());
                socket.close();
            } catch (IOException e0) {
                System.err.println(e.getMessage());
            }
        });

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


    public void shutDown(String msg) {
        System.err.println("exit: " + msg);
        runFlag = false;
    }

    @Override
    public void sendFrame(byte[] frame) {
        int length = frame.length;
        if (frame[length - 1] == 0 && frame[length - 2] == 0) {
            System.out.print("\rdrop frame:");
            return;
        }
        synchronized (clientLock) {
            for (Socket client : clients) {
                try {
                    OutputStream outputStream = client.getOutputStream();
                    sendChunk(_STREAM_BOUNDARY, outputStream);
                    sendChunk(outputStream, _STREAM_PART, ByteUtil.toString(length), DOUBLE_LINE);
                    sendChunk(frame, outputStream);
                    outputStream.flush();
                } catch (IOException e) {
                    checkState(client, e);
                }

            }
        }
    }
}
