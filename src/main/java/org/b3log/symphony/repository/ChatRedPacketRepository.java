package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ChatRedPacket;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;

@Repository
public class ChatRedPacketRepository extends AbstractRepository {
    public ChatRedPacketRepository(){super(ChatRedPacket.TABLE);}
    public JSONObject current(String messageId,boolean lock)throws RepositoryException {
        if(lock&&!hasTransactionBegun())throw new IllegalStateException("packet transaction required");
        final var rows=select("SELECT * FROM `"+getName()+"` WHERE oId=?"+(lock?" FOR UPDATE":""),messageId);
        if(rows.size()>1)throw new RepositoryException("packet duplicate");return rows.isEmpty()?null:rows.getFirst();
    }
    /** Caller already holds current chat-message row; no deletion or refund is performed here. */
    public void requireSettledForRemoval(String messageId)throws RepositoryException {
        final var packet=current(messageId,true);
        if(packet==null)return;
        try {
            if(!(packet.opt("got") instanceof Number gotValue)||!(packet.opt("count") instanceof Number countValue))throw new IllegalArgumentException();
            final int got=new java.math.BigDecimal(gotValue.toString()).intValueExact(),count=new java.math.BigDecimal(countValue.toString()).intValueExact();
            if(count<1||count>100||got<0||got>count)throw new IllegalArgumentException();
            if(got<count)throw new ApiV3Exception(409,"red_packet_unclaimed","红包尚未全部领取，暂不能删除");
        }catch(ApiV3Exception error){throw error;}
        catch(RuntimeException error){throw new ApiV3Exception(503,"temporarily_unavailable","红包状态暂不可用");}
    }
}
