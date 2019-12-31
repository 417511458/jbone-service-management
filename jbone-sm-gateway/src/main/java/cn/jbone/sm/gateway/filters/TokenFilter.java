package cn.jbone.sm.gateway.filters;

import cn.jbone.common.exception.JboneException;
import cn.jbone.common.rpc.Result;
import cn.jbone.sm.gateway.constants.GatewayConstants;
import cn.jbone.sm.gateway.token.SsoTokenInfo;
import cn.jbone.sso.common.domain.UserInfo;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.netflix.ribbon.support.RibbonCommandContext;
import org.springframework.cloud.netflix.ribbon.support.RibbonRequestCustomizer;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class TokenFilter extends ZuulFilter {

    Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 路径匹配器
     */
    private AntPathMatcher pathMatcher = new AntPathMatcher();

    protected ProxyRequestHelper helper;

    private boolean useServlet31;

    protected RibbonCommandFactory<?> ribbonCommandFactory;
    protected List<RibbonRequestCustomizer> requestCustomizers;

    /**
     * URI白名单
     */
    private List<String> uriWhiteList;

    private String ssoServiceId;

    private String ssoMethod = "GET";

    private String ssoProfileUri = "/oauth2.0/profile";

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext requestContext = RequestContext.getCurrentContext();
        HttpServletRequest request = requestContext.getRequest();
        String uri = request.getRequestURI();
        return !isInWhiteList(uri);
    }



    public TokenFilter(ProxyRequestHelper helper, RibbonCommandFactory<?> ribbonCommandFactory, List<RibbonRequestCustomizer> requestCustomizers, List<String> uriWhiteList,String ssoServiceId){
        this.uriWhiteList = uriWhiteList;
        this.useServlet31 = true;
        this.helper = helper;
        this.ribbonCommandFactory = ribbonCommandFactory;
        this.requestCustomizers = requestCustomizers;
        this.ssoServiceId = ssoServiceId;

        try {
            HttpServletRequest.class.getMethod("getContentLengthLong");
        } catch (NoSuchMethodException var5) {
            this.useServlet31 = false;
        }
    }

    /**
     * 白名单内的不用校验权限
     * @param uri 请求的uri
     * @return 是否在白名单内
     */
    private boolean isInWhiteList(String uri){
        if(CollectionUtils.isEmpty(uriWhiteList)){
            return false;
        }

        for (String pattern:uriWhiteList) {
            if(pathMatcher.match(pattern,uri)){
                return true;
            }
        }
        return false;
    }


    protected RibbonCommandContext buildCommandContext(RequestContext context,String token) {
        HttpServletRequest request = context.getRequest();
        MultiValueMap<String, String> headers = this.helper.buildZuulRequestHeaders(request);
        MultiValueMap<String, String> params = this.helper.buildZuulRequestQueryParams(request);
        params.add("access_token",token);
        String verb = this.ssoMethod;

        String serviceId = this.ssoServiceId.toUpperCase();
        Boolean retryable = true;
        String uri = this.ssoProfileUri;
        long contentLength = this.useServlet31 ? request.getContentLengthLong() : (long)request.getContentLength();
        return new RibbonCommandContext(serviceId, verb, uri, retryable, headers, params, null, this.requestCustomizers, contentLength, null);
    }

    @Override
    public Object run() throws ZuulException {
        RequestContext requestContext = RequestContext.getCurrentContext();
        HttpServletRequest request = requestContext.getRequest();
        String token = request.getHeader(GatewayConstants.TOKEN_KEY);
        BufferedReader br = null;
        String result = "";
        try {

            if(StringUtils.isBlank(token)){
                throw new JboneException("token is empty.");
            }

            RibbonCommandContext ribbonCommandContext = buildCommandContext(requestContext,token);

            RibbonCommand command = this.ribbonCommandFactory.create(ribbonCommandContext);

            ClientHttpResponse response = (ClientHttpResponse)command.execute();

            br = new BufferedReader(new InputStreamReader(response.getBody()));
            String line = null;
            while((line = br.readLine())!=null) {
                result += line;
            }

            JSONObject resultObject = JSONObject.parseObject(result);
            SsoTokenInfo tokenInfo = new SsoTokenInfo();
            tokenInfo.setClientId(resultObject.getString("client_id"));
            tokenInfo.setId(resultObject.getString("id"));
            tokenInfo.setService(resultObject.getString("service"));
            tokenInfo.setUserInfo(JSON.parseObject(resultObject.getJSONObject("attributes").getString("userInfo"), UserInfo.class));

            requestContext.set(GatewayConstants.TOKEN_KEY,token);
            //将用户ID保存到上下文，用于网关下游过滤器等使用
            requestContext.set(GatewayConstants.TOKEN_INFO,tokenInfo);
            //将userID保存到头信息中，用于下游微服务获取
            requestContext.addZuulRequestHeader(GatewayConstants.USER_ID,tokenInfo.getUserInfo().getBaseInfo().getId() + "");
        } catch (JboneException e) {
            logger.error("Permission denied.",e);
            authErrorSession(requestContext,token);
        } catch (Exception e) {
            logger.error("Permission denied.",e);
            authErrorSession(requestContext,token);
        }finally {
            if(null!=br){
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private void authErrorSession(RequestContext requestContext,String token){
        requestContext.getResponse().setContentType("text/html;charset=UTF-8");
        requestContext.setSendZuulResponse(false);
        requestContext.setResponseStatusCode(401);
        requestContext.setResponseBody(JSON.toJSONString(Result.wrapError(401,"没有权限")));
    }
}
