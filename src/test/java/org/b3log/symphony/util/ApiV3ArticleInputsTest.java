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
package org.b3log.symphony.util;

import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.ArticleDraft;
import org.b3log.symphony.model.LongArticleColumn;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.List;

/** 验证创建文章的入参边界，数据库和发布相关步骤另行测试。 */
public class ApiV3ArticleInputsTest {
    private static JSONObject valid() { return new JSONObject().put("title", " title ").put("content", "    code\n\nparagraph"); }
    public static void main(final String[] args) {
        final JSONObject normal = ApiV3ArticleInputs.normalize(valid());
        Assert.assertEquals(normal.getString(Article.ARTICLE_TITLE), "title");
        Assert.assertEquals(normal.getString(Article.ARTICLE_CONTENT), "    code\n\nparagraph");
        Assert.assertFalse(normal.has(Article.ARTICLE_AUTHOR_ID)); Assert.assertFalse(normal.has(Article.ARTICLE_IP)); Assert.assertFalse(normal.has(Article.ARTICLE_UA));
        Assert.assertEquals(normal.getInt(Article.ARTICLE_ANONYMOUS), Article.ARTICLE_ANONYMOUS_C_PUBLIC);
        Assert.assertTrue(normal.getBoolean(Article.ARTICLE_COMMENTABLE));
        for (final int type : new int[]{0,1,2,5,6}) {
            final JSONObject input = valid().put("type", type);
            if (type==5) input.put("qnaOfferPoint",20);
            Assert.assertEquals(ApiV3ArticleInputs.normalize(input).getInt(Article.ARTICLE_TYPE),type);
        }
        try { ApiV3ArticleInputs.normalize(valid().put("type",3)); Assert.fail(); }
        catch(final ApiV3Exception expected){Assert.assertEquals(expected.status,403);Assert.assertEquals(expected.code,"thought_disabled");}
        System.out.println("PASS supported article types, disabled thought publishing, no actor metadata and Markdown whitespace preservation");
        for(final JSONObject bad:List.of(valid().put("title"," "),valid().put("title","x".repeat(256)),valid().put("content","abc"),valid().put("content","x".repeat(1024001)),
                valid().put("title","x\ny"),valid().put("content","x\u0001y"),valid().put("type",4),valid().put("type","0"),valid().put("anonymous",1),
                valid().put("showInList",2),valid().put("statement",4),valid().put("authorId","other"),valid().put("articleAuthorId","other"),valid().put("status",0),valid().put("bypassCensor",true),valid().put("apiKey","key"))) {
            try{ApiV3ArticleInputs.normalize(bad);Assert.fail();}catch(final ApiV3Exception expected){}
        }
        for(final JSONObject bad:List.of(valid().put("rewardPoint",-1),valid().put("rewardPoint",1),valid().put("rewardContent","body"),valid().put("rewardPoint",1).put("rewardContent","x".repeat(102401)),
                valid().put("type",5).put("qnaOfferPoint",19),valid().put("qnaOfferPoint",-1),valid().put("qnaOfferPoint",1.5))) {
            try{ApiV3ArticleInputs.normalize(bad);Assert.fail();}catch(final ApiV3Exception expected){}
        }
        final JSONObject paid=ApiV3ArticleInputs.normalize(valid().put("rewardPoint",1).put("rewardContent","paid"));Assert.assertEquals(paid.getString(Article.ARTICLE_REWARD_CONTENT),"paid");
        System.out.println("PASS field/type/length/control-character boundaries and reward/QnA prerequisites");
        final JSONObject column=ApiV3ArticleInputs.normalize(valid().put("type",6).put("columnId","123").put("chapterNo","2147483647").put("columnCoverURL","/images/cover.png").put("draftId","456"));
        Assert.assertEquals(column.getString(LongArticleColumn.COLUMN_ID),"123");Assert.assertEquals(column.getString(ArticleDraft.ARTICLE_DRAFT_ID),"456");
        for(final JSONObject bad:List.of(valid().put("columnId","1 OR 1=1"),valid().put("draftId","9".repeat(20)),valid().put("chapterNo","01"),valid().put("chapterNo","2147483648"),
                valid().put("chapterNo",1),valid().put("columnTitle","x".repeat(65)),valid().put("columnCoverURL","javascript:alert(1)"))) {
            try{ApiV3ArticleInputs.normalize(bad);Assert.fail();}catch(final ApiV3Exception expected){}
        }
        System.out.println("PASS existing cover validation, column/draft identifiers and bounded chapter numbers");
    }
}
