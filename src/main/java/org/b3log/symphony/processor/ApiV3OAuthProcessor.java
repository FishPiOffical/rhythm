package org.b3log.symphony.processor;

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.*;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** OAuth protocol responses are unwrapped; browser consent never inherits Bearer/apiKey identity. */
@Singleton
public class ApiV3OAuthProcessor {
    @Inject private ApiV3AuthorizationRequestService requests;
    @Inject private ApiV3AuthorizationCodeService codes;
    @Inject private ApiV3TokenMgmtService tokens;
    @Inject private ApiV3OAuthCorsService cors;
    @Inject private ApiV3OAuthCredentialQueryService credentials;
    @Inject private AnonymousViewCheckMidware anonymous;
    private static final Set<String> AUTHORIZE=Set.of("response_type","client_id","redirect_uri","scope","state","code_challenge","code_challenge_method");
    private static final Set<String> CONSENT=Set.of("request","proof","decision","csrfToken");
    private static final Set<String> TOKEN=Set.of("grant_type","client_id","redirect_uri","code","code_verifier","refresh_token","scope");
    private static final Set<String> REVOKE=Set.of("client_id","token","token_type_hint");
    public static void register(){
        ApiV3OAuthTransportLogFilter.requireInstalled();
        final var beans=BeanManager.getInstance();final var p=beans.getReference(ApiV3OAuthProcessor.class);final var anonymous=beans.getReference(AnonymousViewCheckMidware.class);
        registerRoutes(p,anonymous);
        beans.getReference(ApiV3OAuthMaintenanceService.class).start();
    }
    public static void registerRoutes(ApiV3OAuthProcessor p,AnonymousViewCheckMidware anonymous){
        p.anonymous=anonymous;
        Dispatcher.get("/api/v3/oauth/authorize",p::authorize,p::privacy,p::rate,anonymous::handle);
        Dispatcher.post("/api/v3/oauth/consent",p::consent,p::privacy,p::rate,p::cookieOnly);
        Dispatcher.post("/api/v3/oauth/token",p::token,p::privacy,p::rate,p::protocolToken);
        Dispatcher.post("/api/v3/oauth/revoke",p::revoke,p::privacy,p::rate,p::protocolRevoke);
    }
    protected void anonymousAccess(RequestContext context){anonymous.handle(context);}
    public void protocolToken(RequestContext context){protocolAccess(context,false);}
    public void protocolRevoke(RequestContext context){protocolAccess(context,true);}
    /** Endpoint-local proof only: never creates a session, Bearer identity, IP whitelist or grant. */
    private void protocolAccess(RequestContext context,boolean revocation){
        JSONObject owner=null;
        try{
            final var values=form(context,revocation?REVOKE:TOKEN);final String client=client(values);
            if(context.header("Authorization")==null&&cors.allowed(client,context.header("Origin"))){
                if(revocation){
                    if(!values.getOrDefault("token_type_hint","").matches("[\\x21-\\x7E]{0,64}"))throw bad();
                    owner=credentials.token(client,required(values,"token",256),true);
                }else if("authorization_code".equals(required(values,"grant_type",64))){
                    if(values.containsKey("refresh_token")||values.containsKey("scope"))throw bad();
                    owner=credentials.code(client,required(values,"redirect_uri",2048),required(values,"code",256),required(values,"code_verifier",128));
                }else if("refresh_token".equals(values.get("grant_type"))){
                    if(values.containsKey("redirect_uri")||values.containsKey("code")||values.containsKey("code_verifier"))throw bad();
                    owner=credentials.token(client,required(values,"refresh_token",256),false);
                }
            }
        }catch(ApiV3Exception|IllegalArgumentException rejected){/* Unauthenticated requests retain the existing anonymous gate. */}
        catch(Exception unavailable){failure(context,unavailable);context.abort();return;}
        if(owner==null){anonymousAccess(context);return;}
        final String actor=owner.optString(Keys.OBJECT_ID);final JSONObject cookie=cookieUser(context);
        if(!actor.matches("[1-9][0-9]{0,18}")){anonymousAccess(context);return;}
        if((cookie==null||!actor.equals(cookie.optString(Keys.OBJECT_ID)))&&!ApiV3OAuthRateLimit.actor(actor)){
            context.setHeader("Retry-After","60");error(context,429,"temporarily_unavailable","请求过于频繁，请稍后重试");context.abort();return;
        }
        // The exchange/revocation core must read and authorize again in its own strict transaction.
        context.handle();
    }
    protected JSONObject cookieUser(RequestContext c){return Sessions.currentCookieUser(c.getRequest());}
    protected String csrf(RequestContext c){return Sessions.getCSRFTokenForUser(cookieUser(c));}
    protected String site(){return Latkes.getServePath();}
    private void common(RequestContext c){
        c.setHeader("Cache-Control","no-store");c.setHeader("Pragma","no-cache");c.setHeader("Referrer-Policy","no-referrer");
        c.setHeader("X-Frame-Options","DENY");c.setHeader("X-Content-Type-Options","nosniff");c.setHeader("X-Request-Id",ApiV3Processor.requestId(c));
        c.setHeader("Content-Security-Policy","default-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; style-src 'self' "+staticOrigin());
    }
    public void privacy(RequestContext c){common(c);c.handle();}
    public void rate(RequestContext c){
        final JSONObject user=cookieUser(c);
        if(!ApiV3OAuthRateLimit.ip(Requests.getRemoteAddr(c.getRequest()))||(user!=null&&!ApiV3OAuthRateLimit.actor(user.optString(Keys.OBJECT_ID)))){
            c.setHeader("Retry-After","60");error(c,429,"temporarily_unavailable","请求过于频繁，请稍后重试");c.abort();return;
        }
        c.handle();
    }
    public void cookieOnly(RequestContext c){
        final JSONObject user=cookieUser(c);if(user==null||!user.optString(Keys.OBJECT_ID).matches("[1-9][0-9]{0,18}")){
            error(c,401,"login_required","请先在本站登录");c.abort();return;
        }
        c.handle();
    }
    private Map<String,String> form(RequestContext c,Set<String> allowed){
        final String query=c.getRequest().getQueryString();if(query!=null&&!query.isEmpty())throw bad();
        final String type=c.header("Content-Type");
        if(type==null||!type.split(";",2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded"))throw new ApiV3Exception(415,"invalid_request","请使用URL编码表单");
        final String[] parameters=type.split(";",-1);boolean charset=false;
        for(int i=1;i<parameters.length;i++){
            if(charset||!parameters[i].trim().matches("(?i)charset\\s*=\\s*(?:UTF-8|\"UTF-8\")"))throw new ApiV3Exception(415,"invalid_request","表单须使用UTF-8");charset=true;
        }
        return ApiV3OAuthForms.parse(c.getRequest().getBytes(),allowed);
    }
    private static String required(Map<String,String> v,String key,int max){return ApiV3OAuthForms.required(v,key,max);}
    private static ApiV3Exception bad(){return new ApiV3Exception(400,"invalid_request","请求参数不合法");}
    private static String client(Map<String,String> form){
        String value=required(form,"client_id",64);
        if(!value.matches("[\\x21-\\x7E]{1,64}")||ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(value))throw new ApiV3Exception(401,"invalid_client","客户端不受支持");return value;
    }
    public void authorize(RequestContext c){
        common(c);try{
            final byte[] body=c.getRequest().getBytes();if(body!=null&&body.length>0)throw bad();
            final String raw=c.getRequest().getQueryString();final var values=ApiV3OAuthForms.parse(raw,AUTHORIZE);
            if(!"code".equals(required(values,"response_type",32)))throw new ApiV3Exception(400,"unsupported_response_type","仅支持授权码");
            String client=client(values),uri=required(values,"redirect_uri",2048),scope=required(values,"scope",255),state=values.getOrDefault("state","");
            String challenge=required(values,"code_challenge",43),method=required(values,"code_challenge_method",16);
            ApiV3Pkce.requireS256(challenge,method);ApiV3OAuthCallbacks.validateState(state);
            if(!ApiV3OAuthRedirects.valid(uri,Latkes.RuntimeMode.DEVELOPMENT==Latkes.getRuntimeMode()))throw bad();
            final JSONObject user=cookieUser(c);final Map<String,Object> model=new HashMap<>();model.put("oauthHome",site());
            model.put("oauthStyle",style());model.put("oauthLoggedIn",user!=null);
            if(user==null){
                c.setStatus(401);model.put("oauthLogin",site()+"/login?goto="+encode(site()+"/api/v3/oauth/authorize?"+raw));
            }else{
                final var prepared=requests.start(user.optString(Keys.OBJECT_ID),client,uri,scope,state,challenge,method);
                model.put("oauthUser",user.optString(User.USER_NAME));model.put("oauthClient",prepared.clientName());model.put("oauthRedirect",prepared.redirectUri());
                model.put("oauthPermissions",permissions(prepared.scope()));model.put("oauthRequest",prepared.requestId());model.put("oauthProof",prepared.consentToken());
                model.put("oauthCsrf",csrf(c));model.put("oauthAction",site()+"/api/v3/oauth/consent");
            }
            render(c,model);
        }catch(Exception e){failure(c,e);}
    }
    protected void render(RequestContext c,Map<String,Object> model){
        final var renderer=new SkinRenderer(c,"verify/oauth-consent.ftl");renderer.getDataModel().putAll(model);c.getResponse().setContentType("text/html; charset=UTF-8");c.setRenderer(renderer);
    }
    public void consent(RequestContext c){
        common(c);try{
            final JSONObject user=cookieUser(c);if(user==null)throw new ApiV3Exception(401,"login_required","请先在本站登录");
            final var values=form(c,CONSENT);
            if(!ApiV3ConsentSecurity.sameOrigin(site(),c.header("Origin"),c.header("Referer"),c.header("Sec-Fetch-Site"))
                    ||!ApiV3ConsentSecurity.validCsrf(csrf(c),values.get("csrfToken")))throw new ApiV3Exception(403,"access_denied","请求来源或页面凭据无效");
            final String decision=required(values,"decision",16);if(!Set.of("approve","deny").contains(decision))throw bad();
            final var result=requests.decide(user.optString(Keys.OBJECT_ID),required(values,"request",256),required(values,"proof",256),"approve".equals(decision));
            c.setStatus(303);c.setHeader("Location",result.redirectUri());json(c,new JSONObject());
        }catch(Exception e){failure(c,e);}
    }
    private void cors(RequestContext c,String client)throws Exception {
        if(c.header("Authorization")!=null)throw new ApiV3Exception(401,"invalid_client","此端点不使用Authorization身份");
        final String origin=c.header("Origin");c.setHeader("Vary","Origin");
        if(!cors.allowed(client,origin))throw new ApiV3Exception(403,"access_denied","请求来源不受支持");
        if(origin!=null)c.setHeader("Access-Control-Allow-Origin",origin);
    }
    public void token(RequestContext c){
        common(c);try{
            final var values=form(c,TOKEN);final String client=client(values);cors(c,client);
            final String grant=required(values,"grant_type",64);final JSONObject result;
            if("authorization_code".equals(grant)){
                if(values.containsKey("refresh_token")||values.containsKey("scope"))throw bad();
                result=codes.exchange(client,required(values,"redirect_uri",2048),required(values,"code",256),required(values,"code_verifier",128));
            }else if("refresh_token".equals(grant)){
                if(values.containsKey("redirect_uri")||values.containsKey("code")||values.containsKey("code_verifier"))throw bad();
                final String scope=values.get("scope");result=tokens.refreshForClient(client,required(values,"refresh_token",256),scope==null||scope.isEmpty()?null:scope);
            }else throw new ApiV3Exception(400,"unsupported_grant_type","授权方式不受支持");
            json(c,new JSONObject().put("access_token",result.getString("accessToken")).put("token_type","Bearer").put("expires_in",result.getLong("expiresIn"))
                    .put("refresh_token",result.getString("refreshToken")).put("refresh_expires_in",result.getLong("refreshExpiresIn")).put("scope",result.getString("scope")));
        }catch(Exception e){failure(c,e);}
    }
    public void revoke(RequestContext c){
        common(c);try{
            final var values=form(c,REVOKE);final String client=client(values);cors(c,client);final String credential=required(values,"token",256);
            if(!values.getOrDefault("token_type_hint","").matches("[\\x21-\\x7E]{0,64}"))throw bad();tokens.revokeTokenForClient(client,credential);json(c,new JSONObject());
        }catch(Exception e){failure(c,e);}
    }
    private void failure(RequestContext c,Exception e){
        if(e instanceof ApiV3Exception api){error(c,api.status,api.code,api.getMessage());return;}
        if(e instanceof IllegalArgumentException){error(c,400,"invalid_request","请求参数不合法");return;}
        if(e instanceof ServiceException&&e.getCause()==null){error(c,400,"invalid_scope".equals(e.getMessage())?"invalid_scope":"invalid_grant","授权凭据无效或权限不受支持");return;}
        LogManager.getLogger(getClass()).warn("OAuth operation unavailable requestId={}",ApiV3Processor.requestId(c));error(c,503,"temporarily_unavailable","授权服务暂不可用");
    }
    private void error(RequestContext c,int status,String code,String description){common(c);c.setStatus(status);json(c,new JSONObject().put("error",code).put("error_description",description));}
    private static void json(RequestContext c,JSONObject value){c.getResponse().setContentType("application/json; charset=UTF-8");c.renderJSON(value);}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private String style(){
        String version=Symphonys.get("staticResourceVersion");if(version==null||version.isEmpty()||Latkes.RuntimeMode.DEVELOPMENT==Latkes.getRuntimeMode())version=Latkes.getStaticResourceVersion();
        return Latkes.getStaticServePath()+"/css/oauth-consent.css?"+encode(version==null?"":version);
    }
    private String staticOrigin(){
        try{final URI uri=URI.create(Latkes.getStaticServePath());if(("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))&&uri.getHost()!=null&&uri.getRawUserInfo()==null)return uri.getScheme()+"://"+uri.getRawAuthority();}catch(IllegalArgumentException ignored){}return "'self'";
    }
    private static List<Map<String,String>> permissions(String scope){
        final List<Map<String,String>> result=new ArrayList<>();for(String key:scope.split(" ")){
            String label=switch(key){case "profile.read"->"读取你的基本账号资料";case "content.read"->"读取你有权访问的文章、评论和聊天室内容";case "content.write"->"管理你的文章并使用聊天室发言及互动功能";
                case "comments.write"->"发表评论、管理你的评论并参与评论互动";case "reactions.write"->"设置或取消表情回应";case "follows.write"->"管理关注、订阅和屏蔽";case "activity.read"->"读取签到、背包和活动状态";case "activity.write"->"参与签到和背包活动";
                case "notifications.read"->"读取你的通知";case "notifications.write"->"管理你的通知";case "settings.write"->"修改你的资料及个人设置";case "uploads.write"->"上传文件";case "messages.read"->"读取你的私信会话与消息";case "messages.write"->"管理你的私信";default->"其他权限："+key;};result.add(Map.of("key",key,"label",label));
        }return List.copyOf(result);
    }
}
