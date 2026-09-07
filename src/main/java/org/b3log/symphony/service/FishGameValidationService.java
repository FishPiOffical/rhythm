package org.b3log.symphony.service;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.FishGame;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import java.net.URI;

/** 鱼游字段校验与规范化服务。 */
@Service
public class FishGameValidationService {
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_URL_LENGTH = 512;

    public JSONObject build(final JSONObject request, final String authorId, final int status)
            throws ServiceException {
        final String name = normalize(request.optString(FishGame.NAME), MAX_NAME_LENGTH, "名称");
        final String description = normalize(request.optString(FishGame.DESCRIPTION), MAX_DESCRIPTION_LENGTH, "描述");
        final String url = normalizeUrl(request.optString(FishGame.URL), "目标网址");
        final String icon = normalizeUrl(request.optString(FishGame.ICON_URL), "图标网址");
        final long now = System.currentTimeMillis();
        final JSONObject result = new JSONObject().put(FishGame.NAME, name).put(FishGame.DESCRIPTION, description)
                .put(FishGame.URL, url).put(FishGame.ICON_URL, icon)
                .put(FishGame.AUTHOR_ID, authorId).put(FishGame.STATUS, status)
                .put(FishGame.EDIT_PENDING, 0).put(FishGame.LIKE_COUNT, 0)
                .put(FishGame.DISLIKE_COUNT, 0).put(FishGame.CREATED_TIME, now)
                .put(FishGame.UPDATED_TIME, now);
        return result;
    }

    private String normalize(final String value, final int max, final String label) throws ServiceException {
        final String normalized = Jsoup.clean(StringUtils.trimToEmpty(value), Whitelist.none()).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new ServiceException(label + "不能为空且不能超过 " + max + " 个字符");
        }
        return normalized;
    }

    private String normalizeUrl(final String value, final String label) throws ServiceException {
        final String normalized = normalize(value, MAX_URL_LENGTH, label);
        try {
            final URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || StringUtils.isBlank(uri.getHost())) {
                throw new ServiceException(label + "必须使用 HTTPS 完整网址");
            }
        } catch (final ServiceException e) {
            throw e;
        } catch (final Exception e) {
            throw new ServiceException(label + "格式不合法");
        }
        return normalized;
    }

}
