package cn.jbone.sm.gateway.filters;

import cn.jbone.common.rpc.Result;
import com.alibaba.fastjson.JSON;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Ip黑名单过滤器
 */
public class IpFilter extends ZuulFilter {

    Logger logger = LoggerFactory.getLogger(getClass());

    private List<String> blackIps;

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return -1;
    }

    @Override
    public boolean shouldFilter() {
        //If the blackIps is empty，the validation is skipped。
        return CollectionUtils.isNotEmpty(blackIps);
    }


    public IpFilter(){}
    public IpFilter(List<String> blackIps){
        this.blackIps = blackIps;
    }

    @Override
    public Object run() throws ZuulException {
        if(CollectionUtils.isEmpty(blackIps)){
            logger.info("The blackIps is empty, the validation is skipped.");
            return null;
        }
        RequestContext requestContext = RequestContext.getCurrentContext();
        HttpServletRequest request = requestContext.getRequest();
        String ip = request.getRemoteAddr();
        if(!blackIps.contains(ip)){
            return null;
        }

        logger.warn("IP check failed.");
        requestContext.getResponse().setContentType("text/html;charset=UTF-8");
        requestContext.setSendZuulResponse(false);
        requestContext.setResponseStatusCode(401);
        requestContext.setResponseBody(JSON.toJSONString(Result.wrapError(401,"没有权限")));

        return null;
    }
}
