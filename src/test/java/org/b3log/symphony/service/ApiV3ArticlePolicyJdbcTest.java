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

import org.b3log.latke.repository.jdbc.util.FieldDefinition;
import org.b3log.latke.repository.jdbc.util.JdbcRepositories;
import org.b3log.latke.repository.jdbc.util.JdbcUtil;
import org.b3log.latke.repository.jdbc.util.RepositoryDefinition;
import org.b3log.symphony.util.ApiV3ArticlePolicy;
import org.b3log.symphony.util.ApiV3ContentSnapshots;
import org.b3log.symphony.util.ApiV3Exception;
import java.sql.DriverManager;
import java.util.List;

/** Runs after the current-content fixture on disposable MySQL, using the bundled real Latke mapper. */
public final class ApiV3ArticlePolicyJdbcTest {
    private static void check(boolean value) { if(!value)throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        String url=System.getProperty("rhythm.content.mysqlUrl");
        if(url==null)throw new IllegalStateException("disposable MySQL fixture required");
        var props=new java.util.Properties();props.setProperty("runtimeDatabase","MYSQL");
        var local=org.b3log.latke.Latkes.class.getDeclaredField("localProps");local.setAccessible(true);local.set(null,props);
        var field=new FieldDefinition();field.setName("articleCommentable");field.setType("boolean");
        var definition=new RepositoryDefinition();definition.setName("articles");definition.setKeys(List.of(field));
        JdbcRepositories.setRepositoryDefinitions(List.of(definition));
        try(var db=DriverManager.getConnection(url)) {
            db.setAutoCommit(false);
            try {
                try(var p=db.createStatement()){p.executeUpdate("UPDATE articles SET articleCommentable='x' WHERE oId='10'");}
                boolean reproduced=false;
                try{JdbcUtil.queryListJson("SELECT * FROM articles WHERE oId=?",List.of("10"),db,"articles",false);}
                catch(Exception expected){for(Throwable cause=expected;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLDataException && cause.getMessage().contains("Cannot determine value type"))reproduced=true;}
                check(reproduced);
                System.out.println("PASS policy JDBC 1 bundled Latke boolean mapping reproduces corrupted CHAR conversion failure before policy code");
                String expectedContent;
                try(var p=db.createStatement();var r=p.executeQuery("SELECT articleContent FROM articles WHERE oId='10'")){check(r.next());expectedContent=r.getString(1);}
                for(String raw:List.of("0","1","2","x","t","f","Y","N")) {
                    try(var p=db.prepareStatement("UPDATE articles SET articleCommentable=? WHERE oId='10'")){p.setString(1,raw);p.executeUpdate();}
                    var row=JdbcUtil.queryListJson(ApiV3ContentSnapshots.article("articles","users","comments"),List.of("10",1,2),db,"articles",false).getFirst();
                    Boolean expected=raw.equals("1")?Boolean.TRUE:raw.equals("0")?Boolean.FALSE:null;
                    check(java.util.Objects.equals(ApiV3ArticlePolicy.commentable(row),expected));
                    check(!row.has("articleCommentable"));
                    check(!row.has("articleIP")&&!row.has("articleUA"));
                    check(row.getString("articleContent").equals(expectedContent));
                    var locked=JdbcUtil.queryListJson(ApiV3ArticlePolicy.lockSql("articles"),List.of("10"),db,"articles",false).getFirst();
                    check(java.util.Objects.equals(ApiV3ArticlePolicy.commentable(locked),expected));
                    if(!Boolean.TRUE.equals(expected)){
                        try{ApiV3ArticlePolicy.requireCommenting(locked);throw new AssertionError();}
                        catch(ApiV3Exception denied){check(denied.status==403);}
                    }
                }
                System.out.println("PASS policy JDBC 2 actual public snapshot and FOR UPDATE SQL preserve exact CHAR 0/1, map other values to unknown and reject new writes");
            } finally {db.rollback();}
        }
    }
}
