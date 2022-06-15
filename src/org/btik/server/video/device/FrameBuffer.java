package org.btik.server.video.device;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * 视频帧缓冲区
 */
public class FrameBuffer extends ByteArrayOutputStream {

    /**
     * 帧分割标识
     */
    private static final byte[] end = new byte[]{'j', 'p', 'e', 'g', '\n'};

    private static final int END_LEN = end.length;

    private static final int END_TOP_INDEX = END_LEN - 1;

    private static final byte endLast = end[END_TOP_INDEX];
    private int checkIndex = END_TOP_INDEX;

    @Override
    public void write(byte[] b, int off, int len) {
        synchronized (this) {
            super.write(b, off, len);
            this.notify();
        }
    }

    /**
     * 此处不加锁，但必须在锁对象为this情况下调用
     */
    boolean hasFrame() {
        if (count < END_LEN) {
            return false;
        }
        searchEndChar:
        for (; checkIndex < count; checkIndex++) {
            if (buf[checkIndex] == endLast) {
                for (int i = checkIndex - 1, j = END_TOP_INDEX - 1; j > 0; i--, j--) {
                    if (buf[i] != end[j]) {
                        continue searchEndChar;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 此处不加锁，但必须在锁对象为this情况下调用
     */
    byte[] takeFrame() {
        int newLength = checkIndex - END_TOP_INDEX;
        byte[] bytes = Arrays.copyOf(buf, newLength);
        int nextIndex = checkIndex + 1;
        count -= nextIndex;
        System.arraycopy(buf, nextIndex, buf, 0, count);
        checkIndex = END_TOP_INDEX;
        return bytes;
    }
}
