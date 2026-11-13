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
package org.b3log.symphony.service;

import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.PetArchiveRepository;
import org.b3log.symphony.repository.PetJobProgressRepository;
import org.b3log.symphony.repository.PetSkillRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.repository.UserRepository;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.List;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

public class ApiV3ArchivedPetProfileTest {
    @Test
    public void archivedProgressUsesTheOriginalPetAndOwner() throws Exception {
        try (final var db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            final var service = new ApiV3RankingQueryService();
            final var owners = new UserRepository() {
                public String getName() { return "users"; }
            };
            final var pets = new UserPetRepository() {
                public String getName() { return "pets"; }
                public List<JSONObject> select(final String query, final Object... params) throws RepositoryException {
                    return rows(db, query, params);
                }
            };
            final var archives = new PetArchiveRepository() {
                public String getName() { return "pet_archive"; }
                public List<JSONObject> select(final String query, final Object... params) throws RepositoryException {
                    return rows(db, query, params);
                }
            };
            final var skills = new PetSkillRepository() {
                public String getName() { return "pet_skill"; }
                public List<JSONObject> select(final String query, final Object... params) throws RepositoryException {
                    return rows(db, query, params);
                }
            };
            final var jobs = new PetJobProgressRepository() {
                public String getName() { return "pet_job_progress"; }
                public List<JSONObject> select(final String query, final Object... params) throws RepositoryException {
                    return rows(db, query, params);
                }
            };
            inject(service, "pets", pets);
            inject(service, "petOwners", owners);
            inject(service, "petArchives", archives);
            inject(service, "petSkills", skills);
            inject(service, "petJobs", jobs);
            inject(service, "dto", new ApiV3ContentDtoService() {
                public JSONObject author(final JSONObject user, final boolean anonymous) {
                    return new JSONObject().put("id", user.getString("oId")).put("userName", user.getString("userName"));
                }
            });
            sql(db, "CREATE TABLE " + owners.getName() + "(oId TEXT,userName TEXT,userNickname TEXT,userAvatarURL TEXT,userStatus INTEGER)");
            sql(db, "INSERT INTO " + owners.getName() + " VALUES('1','owner','','',?)", UserExt.USER_STATUS_C_VALID);
            sql(db, "CREATE TABLE " + pets.getName() + "(oId TEXT,userId TEXT,name TEXT,speciesId TEXT,experience INTEGER,adoptedAt INTEGER)");
            sql(db, "INSERT INTO " + pets.getName() + " VALUES('81','1','新宠物','hexiaotun',0,2000)");
            sql(db, "CREATE TABLE " + archives.getName() + "(oId TEXT,userId TEXT,retiredAt INTEGER,snapshotJson TEXT)");
            final var saved = new JSONObject().put("name", "旧宠物").put("speciesId", "hexiaotun")
                    .put("experience", 400).put("adoptedAt", 1000).put("friendship", 88);
            sql(db, "INSERT INTO " + archives.getName() + " VALUES('80','1',2000,?)", saved.toString());
            sql(db, "CREATE TABLE " + skills.getName() + "(petId TEXT,userId TEXT,skillId TEXT,experience INTEGER)");
            sql(db, "INSERT INTO " + skills.getName() + " VALUES('80','1','vitality',250),('81','1','vitality',0),('80','2','exploration',999)");
            sql(db, "CREATE TABLE " + jobs.getName() + "(petId TEXT,userId TEXT,jobId TEXT,experience INTEGER)");
            sql(db, "INSERT INTO " + jobs.getName() + " VALUES('80','1','delivery-assistant',350),('81','1','delivery-assistant',0)");

            final var publicProfile = service.petProfile("80");
            Assert.assertTrue(publicProfile.getBoolean("archived"));
            Assert.assertEquals(publicProfile.getString("name"), "旧宠物");
            Assert.assertEquals(publicProfile.getJSONObject("user").getString("id"), "1");
            Assert.assertFalse(publicProfile.has("friendship"));
            Assert.assertFalse(publicProfile.has("snapshotJson"));

            final var snapshot = new JSONObject().put("petId", "80").put("speciesId", "hexiaotun")
                    .put("name", "发言时的昵称").put("state", "course");
            final var message = new JSONObject().put("author", new JSONObject().put("id", "1"))
                    .put("createdAt", 1500).put("petStatus", snapshot);
            final var chatProfile = service.chatPetProfile(message);
            Assert.assertTrue(chatProfile.getBoolean("archived"));
            Assert.assertEquals(chatProfile.getString("state"), "course");
            Assert.assertEquals(chatProfile.getJSONArray("learning").toString(), publicProfile.getJSONArray("learning").toString());
            Assert.assertEquals(chatProfile.getJSONArray("career").toString(), publicProfile.getJSONArray("career").toString());
            Assert.assertTrue(chatProfile.getJSONArray("learning").getJSONObject(0).getInt("level") > 1);
            Assert.assertTrue(chatProfile.getJSONArray("career").getJSONObject(0).getInt("level") > 1);
            Assert.assertEquals(chatProfile.getJSONArray("learning").getJSONObject(1).getInt("level"), 1);

            snapshot.remove("petId");
            Assert.assertEquals(service.chatPetProfile(message).getString("id"), "80");
            Assert.assertEquals(service.chatPetProfile(message.put("createdAt", 2001)).getString("id"), "81");
            Assert.assertFalse(service.chatPetProfile(message.put("createdAt", 999)).has("learning"));
            snapshot.put("petId", "80");
            message.getJSONObject("author").put("id", "2");
            Assert.assertFalse(service.chatPetProfile(message).has("learning"));

            sql(db, "UPDATE " + owners.getName() + " SET userStatus=?", UserExt.USER_STATUS_C_INVALID);
            error(404, () -> service.petProfile("80"));
        }
    }
}
