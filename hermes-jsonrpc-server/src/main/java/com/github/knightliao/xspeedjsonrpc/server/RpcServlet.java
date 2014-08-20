package com.github.knightliao.xspeedjsonrpc.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.github.knightliao.apollo.utils.web.HtmlUtils;
import com.github.knightliao.xspeedjsonrpc.core.constant.Constants;
import com.github.knightliao.xspeedjsonrpc.server.handler.HandlerFactory;
import com.github.knightliao.xspeedjsonrpc.server.handler.RpcHandler;
import com.github.knightliao.xspeedjsonrpc.server.model.RpcRequest;

/**
 * 用于处理JsonRpc请求的Servlet
 * 
 * @author liaoqiqi
 * @version 2014-8-20
 */
public class RpcServlet extends HttpServlet {

    private static final long serialVersionUID = 3953082371382281560L;

    protected final Logger log = LoggerFactory.getLogger(RpcServlet.class);

    // 要处理的代理类
    private Map<String, RpcExporter> exporters = new HashMap<String, RpcExporter>();

    // 处理器
    private HandlerFactory handlerFactory = new HandlerFactory();

    /**
     * 显示接口信息
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String context = req.getPathInfo();
        resp.setContentType("text/html");
        PrintWriter out = resp.getWriter();

        //
        // services
        //
        if (context == null || context.equals("/*") || context.equals("/")) {

            out.println("<h1>Server Summary</h1>");
            out.println("This list all supported services");
            out.println("<table border=\"1\"><tr>" + "<th>Service</th>"
                    + "<th>Interface</th>" + "</tr>");

            for (Entry<String, RpcExporter> i : exporters.entrySet()) {
                out.println("<tr><td>" + i.getKey() + "</td><td><a href=\""
                        + req.getContextPath() + req.getServletPath() + "/"
                        + i.getKey() + "\">"
                        + i.getValue().getServiceInterfaceName()
                        + "</a></td></tr>");
            }
            out.println("</table>");

        } else {

            //
            // service's interface
            //
            context = context.substring(1);
            RpcExporter serviceExporter = exporters.get(context);

            if (serviceExporter != null) {

                out.println("<h1>Interface Summary</h1>");

                out.println("This list all functions in <a href=\""
                        + req.getContextPath() + req.getServletPath()
                        + "\"/>service </a>"
                        + serviceExporter.getServiceInterfaceName());

                try {

                    out.println("<table border=\"1\"><tr>" + "<th>Method</th>"
                            + "<th>Signature</th>" + "</tr>");

                    for (Method m : serviceExporter.getServiceInterface()
                            .getMethods()) {
                        out.println("<tr>"
                                + "<td>"
                                + m.getName()
                                + "</td>"
                                + "<td>"
                                + HtmlUtils.escapeHTML(m
                                        .toGenericString()
                                        .substring(16)
                                        .replaceAll("java\\.lang\\.", "")
                                        .replaceAll("java\\.util\\.", "")
                                        .replaceAll(
                                                serviceExporter
                                                        .getServiceInterfaceName()
                                                        + ".", ""))
                                + "</td></tr>");
                    }

                    out.println("</table>");

                } catch (Exception e) {
                    out.println("e.toString()");
                }

            } else {
                resp.setStatus(404);
            }
        }
    }

    /**
     * Constructor of the object.
     */
    public RpcServlet() {
        super();
    }

    /**
     * 初始化时进行接口和目标类的检查
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ApplicationContext factory = null;

        try {

            factory = WebApplicationContextUtils
                    .getWebApplicationContext(config.getServletContext());
            String[] beanNames = factory.getBeanNamesForType(RpcExporter.class);

            if (beanNames == null || beanNames.length == 0) {
                log.warn("There is no RpcExporter configured.");
                return;
            }

            for (String beanName : beanNames) {

                RpcExporter exporter = (RpcExporter) factory.getBean(beanName);
                String context = "";

                try {
                    Class<?> interf = exporter.getServiceInterface();
                    context = interf.getSimpleName();

                    if (interf.isAssignableFrom(exporter.getServiceBean()
                            .getClass())) {
                        exporters.put(context, exporter);
                        log.info("export "
                                + context
                                + " as rpc service,url is http://${server}:${port}/${context}/"
                                + context);

                    } else {
                        log.warn("the interface " + interf.getName()
                                + " is not compatible with the bean "
                                + beanName);
                    }

                } catch (ClassNotFoundException e) {

                    log.warn("the interface "
                            + exporter.getServiceInterfaceName()
                            + " not found.");
                }
            }
        } catch (BeansException e1) {
            log.warn(e1.toString());
        }
    }

    /**
     * Destruction of the servlet. <br>
     */
    public void destroy() {
        super.destroy();
    }

    /**
     * The doPost method of the servlet. <br>
     * 
     * This method is called when a form has its tag value method equals to
     * post.
     * 
     * @param request
     *            the request send by the client to the server
     * @param response
     *            the response send by the server to the client
     * @throws ServletException
     *             if an error occurred
     * @throws IOException
     *             if an error occurred
     */
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String context = request.getPathInfo();
        if (context == null) {
            log.warn("invalid request");
            response.setStatus(404);
            return;
        }

        if (context.length() > 0) {
            context = context.substring(1);
        }

        //
        // get content type
        //
        String encoding = request.getCharacterEncoding();
        String contentType = request.getContentType().split(";")[0];
        if (contentType == null) {
            contentType = Constants.JSON_PROTOCOL_TYPE;
        } else {
            contentType = contentType.toLowerCase();
        }

        //
        // get exporters
        //
        RpcExporter serviceExporter = exporters.get(context);
        if (serviceExporter == null) {

            response.setStatus(400);

        } else {

            try {

                // 读取请求
                byte[] bytes = readStream(request.getInputStream(),
                        request.getContentLength());

                // 组装请求
                RpcRequest rpcReq = new RpcRequest(
                        serviceExporter.getServiceInterface(),
                        serviceExporter.getServiceBean(), bytes, encoding);

                //
                // 处理器
                //
                RpcHandler handler = handlerFactory
                        .getProtocolHandler(contentType);

                // 处理
                handler.service(rpcReq);

                // 设置返回
                response.setContentType(contentType);
                response.setContentLength(rpcReq.response.length);
                response.setCharacterEncoding(encoding);
                response.getOutputStream().write(rpcReq.response);

            } catch (Exception e) {

                log.warn(e.toString());
                response.setStatus(500);
            }
        }
    }

    /**
     * 从stream中读取全部数据
     * 
     * @param input
     * @param length
     * @return 读取到的数据
     * @throws IOException
     */
    private byte[] readStream(InputStream input, int length) throws IOException {

        byte[] bytes = new byte[length];

        int offset = 0;

        while (offset < bytes.length) {

            int bytesRead = input.read(bytes, offset, bytes.length - offset);
            if (bytesRead == -1)
                break; // end of stream
            offset += bytesRead;
        }

        return bytes;
    }
}