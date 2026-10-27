package org.b3log.symphony.model;

/** Durable packet; recipient assignments never enter the public chat envelope. */
public final class ChatRedPacket {
    private ChatRedPacket() { }
    public static final String TABLE="chat_red_packet";
    public static final String SLOT_TABLE="chat_red_packet_slot";
    public static final String MARKER="durable_v3";
}
