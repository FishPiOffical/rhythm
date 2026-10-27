package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ChatRedPacket;
import org.json.JSONObject;
import java.util.List;

@Repository
public class ChatRedPacketSlotRepository extends AbstractRepository {
    public ChatRedPacketSlotRepository(){super(ChatRedPacket.SLOT_TABLE);}
    public List<JSONObject> current(String messageId,boolean lock)throws RepositoryException {
        if(lock&&!hasTransactionBegun())throw new IllegalStateException("packet slot transaction required");
        return select("SELECT * FROM `"+getName()+"` WHERE messageId=? ORDER BY ordinal LIMIT 101"+(lock?" FOR UPDATE":""),messageId);
    }
}
