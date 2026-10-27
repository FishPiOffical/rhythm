package org.b3log.latke.http;

/** Narrow compatibility adapter for bundled Latke: parseForm normalizes getBytes() through lossy UTF-8. */
public final class ApiV3GameSaveRawBody {
    private ApiV3GameSaveRawBody() { }
    public static final class TooLarge extends IllegalArgumentException {
        private TooLarge(){super("request body too large");}
    }
    /** Copy original Netty bytes without moving its reader index or retaining the buffer. */
    public static byte[] copy(Request request,int maximum) {
        if(request==null||request.req==null||maximum<0)throw new IllegalStateException("raw request body unavailable");
        final var body=request.req.content();final int length=body.readableBytes();
        if(length>maximum)throw new TooLarge();final byte[] result=new byte[length];body.getBytes(body.readerIndex(),result);return result;
    }
}
