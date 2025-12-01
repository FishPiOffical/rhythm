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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.service.OptionMgmtService;
import org.b3log.symphony.service.OptionQueryService;
import org.json.JSONArray;
import org.json.JSONObject;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

public class ReservedWords {

    private static final Logger LOGGER = LogManager.getLogger(ReservedWords.class);

    private static OptionMgmtService optionMgmtService;

    private static OptionQueryService optionQueryService;

    private static final String OPTION_NAME = "reservedWordList";

    private static final List<String> CACHE = new ArrayList<>();

    public static String processReservedWord(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }

        try {
            HanyuPinyinOutputFormat hanyuPinyinOutputFormat = new HanyuPinyinOutputFormat();
            hanyuPinyinOutputFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
            hanyuPinyinOutputFormat.setToneType(HanyuPinyinToneType.WITH_TONE_MARK);
            hanyuPinyinOutputFormat.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);

            List<String> list = getCache();
            for (String i : list) {
                if (i.matches("[\\u4e00-\\u9fa5]+")) {
                    content = content.replaceAll(i, PinyinHelper.toHanYuPinyinString(i, hanyuPinyinOutputFormat, " ", false));
                } else {
                    StringBuilder output = new StringBuilder();
                    for (int j = 0; j < i.length(); j++) {
                        output.append("❤️");
                    }
                    content = content.replaceAll(i, output.toString());
                }
            }

            return content;
        } catch (final Exception e) {
            return "本段敏感字处理时出现错误，请联系管理员。";
        }
    }

    public static void init() {
        final BeanManager beanManager = BeanManager.getInstance();
        optionMgmtService = beanManager.getReference(OptionMgmtService.class);
        optionQueryService = beanManager.getReference(OptionQueryService.class);
        JSONObject option = optionQueryService.getOption(OPTION_NAME);
        if (null == option) {
            JSONObject addOption = new JSONObject();
            addOption.put(Keys.OBJECT_ID, OPTION_NAME);
            addOption.put(Option.OPTION_CATEGORY, "reversed-word-list");
            addOption.put(Option.OPTION_VALUE, "[]");
            optionMgmtService.addOption(addOption);
            System.out.println(">>> Reserved word list is empty and generated.");
        } else {
            JSONArray array = new JSONArray(option.optString(Option.OPTION_VALUE));
            for (int i = 0; i < array.length(); i++) {
                String word = String.valueOf(array.get(i));
                CACHE.add(word);
            }
        }
        System.out.println(">>> Reserved words system is ready.");
    }

    public static void add(String word) {
        if (!CACHE.contains(word)) {
            JSONObject option = optionQueryService.getOption(OPTION_NAME);
            JSONArray array = new JSONArray(option.optString(Option.OPTION_VALUE));
            array.put(word);
            JSONObject addOption = new JSONObject();
            addOption.put(Keys.OBJECT_ID, OPTION_NAME);
            addOption.put(Option.OPTION_CATEGORY, "reversed-word-list");
            addOption.put(Option.OPTION_VALUE, array.toString());
            optionMgmtService.updateOption(OPTION_NAME, addOption);
            CACHE.add(word);
        }
    }

    public static void remove(String word) {
        if (CACHE.contains(word)) {
            JSONObject option = optionQueryService.getOption(OPTION_NAME);
            JSONArray array = new JSONArray(option.optString(Option.OPTION_VALUE));
            for (int i = array.length() - 1; i >= 0; i--) {
                if (String.valueOf(array.get(i)).equals(word)) {
                    array.remove(i);
                }
            }
            JSONObject addOption = new JSONObject();
            addOption.put(Keys.OBJECT_ID, OPTION_NAME);
            addOption.put(Option.OPTION_CATEGORY, "reversed-word-list");
            addOption.put(Option.OPTION_VALUE, array.toString());
            optionMgmtService.updateOption(OPTION_NAME, addOption);
            for (int i = CACHE.size() - 1; i >= 0; i--) {
                if (CACHE.get(i).equals(word)) {
                    CACHE.remove(i);
                }
            }
        }
    }

    public static void update(String word, String newWord) {
        remove(word);
        add(newWord);
    }

    public static JSONObject get(String word) {
        if (CACHE.contains(word)) {
            JSONObject option = new JSONObject();
            option.put(Keys.OBJECT_ID, word);
            option.put(Option.OPTION_CATEGORY, word);
            option.put(Option.OPTION_VALUE, word);
            return option;
        }
        return new JSONObject();
    }

    public static List<String> getCache() {
        return CACHE;
    }

    public static List<JSONObject> getList() {
        List<JSONObject> list = new ArrayList<>();
        for (String i : CACHE) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(Option.OPTION_VALUE, i);
            jsonObject.put(Option.OPTION_CATEGORY, i);
            jsonObject.put(Keys.OBJECT_ID, i);
            list.add(jsonObject);
        }
        return list;
    }

    public static boolean contains(String word) {
        return CACHE.contains(word);
    }
}
