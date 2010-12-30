package org.jahia.modules.filter;


import au.id.jericho.lib.html.Attribute;
import au.id.jericho.lib.html.Attributes;
import au.id.jericho.lib.html.OutputDocument;
import au.id.jericho.lib.html.Source;
import au.id.jericho.lib.html.StartTag;
import au.id.jericho.lib.html.Tag;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.log4j.Logger;
import org.jahia.modules.Rewriter.WebClippingRewriter;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import ucar.nc2.util.net.EasySSLProtocolSocketFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Dorth
 * Date: 24 d�c. 2010
 * Time: 16:10:23
 * To change this template use File | Settings | File Templates.
 */
public class WebClippingFilter extends AbstractFilter {
    static private final Logger log = Logger.getLogger(WebClippingFilter.class);
    static private final MultiThreadedHttpConnectionManager HTPP_CONNECTION_MANAGER = new MultiThreadedHttpConnectionManager();

    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        if (resource.getNode().hasProperty("j:url") && resource.getNode().isNodeType("jnt:webClipping")) {
            HttpClient httpClient = new HttpClient();
            Protocol.registerProtocol("https", new Protocol("https", new EasySSLProtocolSocketFactory(), 443));
            httpClient.getParams().setContentCharset("UTF-8");

            String url = resource.getNode().getPropertyAsString("j:url");
            HttpMethodBase httpMethod = new GetMethod(url);
            httpMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
            httpClient.getParams().getContentCharset();

            try {
                httpMethod.getParams().setParameter("http.connection.timeout", "0");
                httpMethod.getParams().setParameter("http.protocol.expect-continue", "false");
                httpMethod.getParams().setCookiePolicy(CookiePolicy.RFC_2965);
                int statusCode = httpClient.executeMethod(httpMethod);
                
                if (statusCode == HttpStatus.SC_MOVED_TEMPORARILY || statusCode == HttpStatus.SC_MOVED_PERMANENTLY
                        || statusCode == HttpStatus.SC_SEE_OTHER || statusCode == HttpStatus.SC_TEMPORARY_REDIRECT) {
                    if (log.isDebugEnabled()) {
                        log.debug("We follow a redirection ");
                    }
                    String redirectLocation;
                    Header locationHeader = httpMethod.getResponseHeader("location");
                    if (locationHeader != null) {
                        redirectLocation = locationHeader.getValue();
                        if (!redirectLocation.startsWith("http")) {
                            URL siteURL = new URL(url);
                            String tmpURL = siteURL.getProtocol() + "://" + siteURL.getHost() + ((siteURL.getPort() > 0) ? ":" + siteURL.getPort() : "") + "/" + redirectLocation;
                            httpMethod = new GetMethod(tmpURL);
                            // Set a default retry handler (see httpclient doc).
                            httpMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
                        } else {
                            httpMethod = new GetMethod(redirectLocation);
                        }
                        if (log.isDebugEnabled()) {
                            log.debug("redirected to : " + redirectLocation);
                        }
                    }
                }

                String[] type = httpMethod.getResponseHeader("Content-Type").getValue().split(";");
                String contentType = type[0];
                String contentCharset = "UTF-8";
                if (type.length == 2) {
                    contentCharset = type[1].split("=")[1];
                }
                InputStream inputStream = new BufferedInputStream(httpMethod.getResponseBodyAsStream());
                if (inputStream != null) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(100 * 1024);
                    byte[] buffer = new byte[100 * 1024];
                    int len;
                    while ((len = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.close();
                    inputStream.close();
                    final byte[] responseBodyAsBytes = outputStream.toByteArray();
                    if (contentType.startsWith("text")) {
                        String responseBody = new String(responseBodyAsBytes, "US-ASCII");
                        Source source = new Source(responseBody);
                        List list = source.findAllStartTags(Tag.META);
                        for (int i = 0; i < list.size(); i++) {
                            StartTag startTag = (StartTag) list.get(i);
                            Attributes attributes = startTag.getAttributes();
                            final Attribute attribute = attributes.get("http-equiv");
                            if (attribute != null && attribute.getValue().equalsIgnoreCase("content-type")) {
                                type = attributes.get("content").getValue().split(";");
                                if (type.length == 2) {
                                    contentCharset = type[1].split("=")[1];
                                }
                            }
                        }
                        final String s = contentCharset.toUpperCase();
                        return rewriteBody(new String(responseBodyAsBytes, s), resource.getNode().getPropertyAsString("j:url"), s, resource, renderContext);
                    } else {
                        return new String(responseBodyAsBytes, contentCharset.toUpperCase());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            return null;
        }
        return null;
    }

    private String rewriteBody(String body, String url, String charset, Resource resource, RenderContext context) throws IOException {
        OutputDocument document;
        document = new WebClippingRewriter(url).rewriteBody(body, resource, context);
        return document.toString();
    }
}
