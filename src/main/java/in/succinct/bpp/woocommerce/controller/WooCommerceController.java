package in.succinct.bpp.woocommerce.controller;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.routing.Config;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.Request;
import in.succinct.bpp.woocommerce.helpers.WooCommerceHelper;
import in.succinct.bpp.shell.task.BppActionTask;
import in.succinct.bpp.shell.util.BecknUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

import static in.succinct.bpp.shell.util.BecknUtil.log;

public class WooCommerceController extends Controller {
    public WooCommerceController(Path path) {
        super(path);
    }

    WooCommerceHelper helper = new WooCommerceHelper();

    @RequireLogin(value = false)
    public View hook() throws Exception{
        String payload = StringUtil.read(getPath().getInputStream());
        if (ObjectUtil.equals(getPath().getHeader("X-WC-Webhook-Topic"),"order.updated")){
            String sign = getPath().getHeader("X-WC-Webhook-Signature");
            Mac mac =Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    Config.instance().getProperty("in.succinct.bpp.woocommerce.hmac.key").getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] hmacbytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            if (!ObjectUtil.equals(Crypt.getInstance().toBase64(hmacbytes),sign)){
                throw new RuntimeException("Webhook - Signature failed!!");
            }
            JSONObject wooOrder = (JSONObject) JSONValue.parse(payload);
            wooOrder = helper.woo_get("/orders/" + wooOrder.get("id"),new JSONObject()); //Need to call to get items array etc.


            Config.instance().getLogger(getClass().getName()).log(Level.WARNING,payload);
            final Request request = new Request();
            request.setMessage(new Message());
            request.setContext(new Context());
            request.getMessage().setOrder(helper.getBecknOrder(wooOrder));
            Context context = request.getContext();
            context.setBppId(BecknUtil.getSubscriberId());
            context.setBppUri(BecknUtil.getSubscriberUrl());
            JSONArray metaArray = (JSONArray) wooOrder.get("meta_data");
            for (int i = 0 ; i < metaArray.size() ; i ++ ){
                JSONObject meta = (JSONObject) metaArray.get(i);
                String key = (String)meta.get("key");
                String value = (String)meta.get("value");
                if (key.startsWith("context.")){
                    String ck = key.substring("context.".length());
                    context.set(ck,value);
                }
            }
            context.setTimestamp(new Date());
            context.setAction("on_status");
            context.setMessageId(UUID.randomUUID().toString());
            TaskManager.instance().executeAsync(new BppActionTask() {
                @Override
                public Request generateCallBackRequest() {
                    registerSignatureHeaders("Authorization");
                    log("ToApplication",request,new HashMap<>(),request,"/" + request.getContext().getAction());
                    return request;
                }
            },false);
        }

        return new BytesView(getPath(),new byte[]{}, MimeType.APPLICATION_JSON){
            @Override
            public void write() throws IOException {
                super.write(HttpServletResponse.SC_NO_CONTENT);
            }
        };
    }
}
