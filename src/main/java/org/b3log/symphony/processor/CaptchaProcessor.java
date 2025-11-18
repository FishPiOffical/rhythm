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
package org.b3log.symphony.processor;

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.http.renderer.PngRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Strings;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import org.patchca.background.BackgroundFactory;
import org.patchca.color.GradientColorFactory;
import org.patchca.color.RandomColorFactory;
import org.patchca.filter.predefined.CurvesRippleFilterFactory;
import org.patchca.font.RandomFontFactory;
import org.patchca.service.Captcha;
import org.patchca.service.ConfigurableCaptchaService;
import org.patchca.word.RandomWordFactory;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * Captcha processor.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 3.0.0.0, Feb 11, 2020
 * @since 0.2.2
 */
@Singleton
public class CaptchaProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(CaptchaProcessor.class);

    /**
     * Key of captcha.
     */
    public static final String CAPTCHA = "captcha";

    /**
     * Captchas.
     */
    public static final Set<String> CAPTCHAS = new HashSet<>();

    /**
     * Captcha length.
     */
    private static final int CAPTCHA_LENGTH = 4;

    /**
     * Captcha chars.
     */
    private static final String CHARS = "acdefhijklmnprstuvwxy234578";

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final CaptchaProcessor captchaProcessor = beanManager.getReference(CaptchaProcessor.class);

        Dispatcher.get("/test", captchaProcessor::captcha);
        Dispatcher.post("/validateCaptcha", captchaProcessor::validateCaptcha);
        Dispatcher.get("/captcha", captchaProcessor::get);
        Dispatcher.get("/captcha/login", captchaProcessor::getLoginCaptcha);
    }

    public void captcha(final RequestContext context) {
        final String ip = Requests.getRemoteAddr(context.getRequest());
        // 黑名单判断
        if (AnonymousViewCheckMidware.ipBlacklistCache.getIfPresent(ip) != null) {
            final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "captcha.ftl");
            final Map<String, Object> dataModel = renderer.getDataModel();
        } else {
            context.sendRedirect("/");
        }
    }

    public void validateCaptcha(final RequestContext context) {
        final JSONObject requestJSONObject = context.requestJSON();
        final String captcha = requestJSONObject.optString(CaptchaProcessor.CAPTCHA);
        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (CaptchaProcessor.jiyan(captcha)) {
            AnonymousViewCheckMidware.ipBlacklistCache.invalidate(ip);
            AnonymousViewCheckMidware.ipVisitCountCache.invalidate(ip);
            context.renderJSON(StatusCodes.SUCC);
            System.out.println(ip + " 验证成功");
        } else {
            context.renderJSON(StatusCodes.ERR);
            System.out.println(ip + " 验证失败");
        }
    }

    public static boolean jiyan(String captcha) {
        try {
            String captchaId = Symphonys.get("jiyan.id");
            String captchaKey = Symphonys.get("jiyan.key");
            String domain = Symphonys.get("jiyan.domain");

            JSONObject c = new JSONObject(captcha);
            String lotNumber = c.optString("lot_number");
            String captchaOutput = c.optString("captcha_output");
            String passToken = c.optString("pass_token");
            String genTime = c.optString("gen_time");

            String signToken = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, captchaKey).hmacHex(lotNumber);

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("lot_number", lotNumber);
            queryParams.put("captcha_output", captchaOutput);
            queryParams.put("pass_token", passToken);
            queryParams.put("gen_time", genTime);
            queryParams.put("sign_token", signToken);

            String url = String.format(domain + "/validate" + "?captcha_id=%s", captchaId);
            JSONObject jsonObject = new JSONObject();

            try {
                HttpResponse response = HttpRequest.post(url)
                        .form("lot_number", lotNumber)
                        .form("captcha_output", captchaOutput)
                        .form("pass_token", passToken)
                        .form("gen_time", genTime)
                        .form("sign_token", signToken)
                        .charset("UTF-8")
                        .timeout(5000)
                        .send();
                if (response.statusCode() == 200) {
                    jsonObject = new JSONObject(response.bodyText());
                } else {
                    throw new IOException("HTTP Status: " + response.statusCode());
                }
            } catch (Exception e) {
                jsonObject.put("result", "fail");
                jsonObject.put("reason", "request geetest api fail");
            }
            if (jsonObject.getString("result").equals("success")) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether the specified captcha is invalid.
     *
     * @param captcha the specified captcha
     * @return {@code true} if it is invalid, returns {@code false} otherwise
     */
    public static boolean invalidCaptcha(final String captcha) {
        if (StringUtils.isBlank(captcha) || captcha.length() != CAPTCHA_LENGTH) {
            return true;
        }

        boolean ret = !CaptchaProcessor.CAPTCHAS.contains(captcha);
        if (!ret) {
            CaptchaProcessor.CAPTCHAS.remove(captcha);
        }

        return ret;
    }

    /**
     * Gets captcha.
     *
     * @param context the specified context
     */
    SimpleCurrentLimiter captchaCurrentLimiter = new SimpleCurrentLimiter(5, 5);
    public void get(final RequestContext context) {
        String address = Requests.getRemoteAddr(context.getRequest());
        if (captchaCurrentLimiter.access(address)) {
            final PngRenderer renderer = new PngRenderer();
            context.setRenderer(renderer);

            try {
                final ConfigurableCaptchaService cs = new ConfigurableCaptchaService();
                // 随机颜色
                if (0.5 < Math.random()) {
                    cs.setColorFactory(new GradientColorFactory());
                } else {
                    cs.setColorFactory(new RandomColorFactory());
                }
                // 随机字符
                final RandomWordFactory randomWordFactory = new RandomWordFactory();
                randomWordFactory.setCharacters(CHARS);
                randomWordFactory.setMinLength(CAPTCHA_LENGTH);
                randomWordFactory.setMaxLength(CAPTCHA_LENGTH);
                cs.setWordFactory(randomWordFactory);
                // 随机字体
                List<String> fonts = getAvailableFonts();
                cs.setFontFactory(new RandomFontFactory(fonts));
                // 自定义验证码图片背景
                MyCustomBackgroundFactory backgroundFactory = new MyCustomBackgroundFactory();
                cs.setBackgroundFactory(backgroundFactory);
                // 彩条
                cs.setFilterFactory(new CurvesRippleFilterFactory(cs.getColorFactory()));

                final Captcha captcha = cs.getCaptcha();
                final String challenge = captcha.getChallenge();
                final BufferedImage bufferedImage = captcha.getImage();

                if (CAPTCHAS.size() > 64) {
                    CAPTCHAS.clear();
                }

                CAPTCHAS.add(challenge);

                final Response response = context.getResponse();
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Expires", "0");

                renderImg(renderer, bufferedImage);
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, e.getMessage(), e);
            }
        } else {
            try {
                final PngRenderer renderer = new PngRenderer();
                context.setRenderer(renderer);

                final Response response = context.getResponse();
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Expires", "0");

                URL url = new URL("http://localhost:8080/images/wait.png");
                renderImg(renderer, ImageIO.read(url));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets captcha for login.
     *
     * @param context the specified context
     */
    public void getLoginCaptcha(final RequestContext context) {
        try {
            final Response response = context.getResponse();

            final String userId = context.param(Common.NEED_CAPTCHA);
            if (StringUtils.isBlank(userId)) {
                return;
            }

            final JSONObject wrong = LoginProcessor.WRONG_PWD_TRIES.get(userId);
            if (null == wrong) {
                return;
            }

            if (wrong.optInt(Common.WRON_COUNT) < 3) {
                return;
            }

            String address = Requests.getRemoteAddr(context.getRequest());
            if (captchaCurrentLimiter.access(address)) {

                final PngRenderer renderer = new PngRenderer();
                context.setRenderer(renderer);

                final ConfigurableCaptchaService cs = new ConfigurableCaptchaService();

                // 随机颜色
                if (0.5 < Math.random()) {
                    cs.setColorFactory(new GradientColorFactory());
                } else {
                    cs.setColorFactory(new RandomColorFactory());
                }
                // 随机字符
                final RandomWordFactory randomWordFactory = new RandomWordFactory();
                randomWordFactory.setCharacters(CHARS);
                randomWordFactory.setMinLength(CAPTCHA_LENGTH);
                randomWordFactory.setMaxLength(CAPTCHA_LENGTH);
                cs.setWordFactory(randomWordFactory);
                // 随机字体
                List<String> fonts = getAvailableFonts();
                cs.setFontFactory(new RandomFontFactory(fonts));
                // 自定义验证码图片背景
                MyCustomBackgroundFactory backgroundFactory = new MyCustomBackgroundFactory();
                cs.setBackgroundFactory(backgroundFactory);
                // 彩条
                cs.setFilterFactory(new CurvesRippleFilterFactory(cs.getColorFactory()));

                final Captcha captcha = cs.getCaptcha();
                final String challenge = captcha.getChallenge();
                final BufferedImage bufferedImage = captcha.getImage();

                wrong.put(CAPTCHA, challenge);

                response.setHeader("Pragma", "no-cache");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Expires", "0");

                renderImg(renderer, bufferedImage);
            } else {
                try {
                    final PngRenderer renderer = new PngRenderer();
                    context.setRenderer(renderer);

                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Cache-Control", "no-cache");
                    response.setHeader("Expires", "0");

                    URL url = new URL("http://localhost:8080/images/wait.png");
                    renderImg(renderer, ImageIO.read(url));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);
        }
    }

    private void renderImg(final PngRenderer renderer, final BufferedImage bufferedImage) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "png", baos);
            final byte[] data = baos.toByteArray();
            renderer.setData(data);
        }
    }

    private static List<String> getAvailableFonts() {
        final List<String> ret = new ArrayList<>();

        final GraphicsEnvironment e = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final Font[] fonts = e.getAllFonts();
        for (final Font f : fonts) {
            if (Strings.contains(f.getFontName(), new String[]{"Verdana", "DejaVu Sans Mono", "Tahoma"})) {
                ret.add(f.getFontName());
            }
        }

        //final String defaultFontName = new JLabel().getFont().getFontName();
        //ret.add(defaultFontName);

        return ret;
    }

    /**
     * 自定义验证码图片背景,主要画一些噪点和干扰线
     */
    private class MyCustomBackgroundFactory implements BackgroundFactory {
        private Random random = new Random();

        public void fillBackground(BufferedImage image) {
            Graphics graphics = image.getGraphics();

            // 验证码图片的宽高
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();

            // 填充为灰色背景
            graphics.setColor(Color.GRAY);
            graphics.fillRect(0, 0, imgWidth, imgHeight);

            // 画100个噪点(颜色及位置随机)
            for(int i = 0; i < 100; i++) {
                // 随机颜色
                int rInt = random.nextInt(255);
                int gInt = random.nextInt(255);
                int bInt = random.nextInt(255);

                graphics.setColor(new Color(rInt, gInt, bInt));

                // 随机位置
                int xInt = random.nextInt(imgWidth - 3);
                int yInt = random.nextInt(imgHeight - 2);

                // 随机旋转角度
                int sAngleInt = random.nextInt(360);
                int eAngleInt = random.nextInt(360);

                // 随机大小
                int wInt = random.nextInt(6);
                int hInt = random.nextInt(6);

                graphics.fillArc(xInt, yInt, wInt, hInt, sAngleInt, eAngleInt);

                // 画5条干扰线
                if (i % 20 == 0) {
                    int xInt2 = random.nextInt(imgWidth);
                    int yInt2 = random.nextInt(imgHeight);
                    graphics.drawLine(xInt, yInt, xInt2, yInt2);
                }
            }
        }
    }
}
