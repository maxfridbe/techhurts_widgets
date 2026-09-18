package com.kunal52.wire;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.io.InputStream;

public abstract class PacketParser extends Thread {
    private final InputStream mInputStream;

    private boolean isAbort = false;


    public PacketParser(InputStream inputStream) {
        mInputStream = inputStream;
    }

    @Override
    public void run() {
        int available;
        int bytesRead = 0;
        while (!isAbort)
            try {
                available = mInputStream.read();
                if (available < 0) {
                    // End of stream: the TV closed the session. Allocating a
                    // byte[-1] here threw NegativeArraySizeException and took
                    // the app down with it.
                    isAbort = true;
                    connectionClosed(new IOException("stream closed by peer"));
                    return;
                }
                byte[] buf = new byte[available];
                while (bytesRead < available) {
                    int read = mInputStream.read(buf, bytesRead, available - bytesRead);
                    if (read < 0) {
                        throw new IOException("Stream closed while reading.");
                    }
                    bytesRead += read;
                }

                bytesRead = 0;
                messageBufferReceived(buf);
            } catch (IOException e) {
                // A closed socket is the normal end of a session (disconnect,
                // or the TV dropping us). Rethrowing killed the whole app from
                // this background thread, so just stop reading.
                isAbort = true;
                connectionClosed(e);
            }
    }


    public void abort() {
        isAbort = true;
    }

    /** Called once when the stream ends; override to react to a lost link. */
    public void connectionClosed(IOException cause) {
    }

    public abstract void messageBufferReceived(byte[] buf);
}
