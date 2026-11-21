/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.ioc.Inject;
import org.b3log.symphony.util.ApiV3ChatTransaction;
import org.json.JSONObject;

@Repository
public class ChatRoomRepository extends AbstractRepository {
    @Inject private ApiV3ChatEventRepository changes;

    /**
     * Public constructor.
     */
    public ChatRoomRepository() { super("chat_room"); }

    @Override
    public String add(JSONObject jsonObject) throws RepositoryException {
        return ApiV3ChatTransaction.write(()->{
            final String id=insertMessage(jsonObject);
            changes.appendInCurrentTransaction(id,"created");return id;
        });
    }
    protected String insertMessage(JSONObject row)throws RepositoryException{return super.add(row);}
    protected void updateMessage(String id,JSONObject row,String...fields)throws RepositoryException{super.update(id,row,fields);}
    protected void removeMessage(String id)throws RepositoryException{super.remove(id);}
    private boolean existsForUpdate(String id)throws RepositoryException {
        return !select("SELECT oId FROM `"+getName()+"` WHERE oId=? FOR UPDATE",id).isEmpty();
    }
    @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException {
        ApiV3ChatTransaction.write(()->{
            if(existsForUpdate(id)){updateMessage(id,row,fields);changes.appendInCurrentTransaction(id,"updated");}
            return null;
        });
    }
    @Override public void remove(String id)throws RepositoryException {
        ApiV3ChatTransaction.write(()->{
            if(existsForUpdate(id)){removeMessage(id);changes.appendInCurrentTransaction(id,"deleted");}
            return null;
        });
    }
}
