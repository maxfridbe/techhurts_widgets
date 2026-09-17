package com.kunal52.wire;

import com.kunal52.AndroidRemoteTv;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class MessageManager {

    private static class Logger {
        public void debug(String msg, Object arg) { Log.d("MessageManager", String.format(msg.replace("{}", "%s"), arg)); }
    }
    private final Logger logger = new Logger();
    public ByteBuffer mPacketBuffer = ByteBuffer.allocate(65539);

    public byte[] addLengthAndCreate(byte[] message) {
        int length = message.length;
        mPacketBuffer.put((byte) length).put(message);
        byte[] buf = new byte[mPacketBuffer.position()];
        System.arraycopy(mPacketBuffer.array(), mPacketBuffer.arrayOffset(), buf, 0, mPacketBuffer.position());
        mPacketBuffer.clear();
        logger.debug("Sending bytes {}", Arrays.toString(buf));
        return buf;
    }


}
