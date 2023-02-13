package in.succinct.bpp.woocommerce.extensions;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.Request;
import in.succinct.bpp.core.adaptor.CommerceAdaptor;
import in.succinct.bpp.core.adaptor.NetworkAdaptor;
import in.succinct.bpp.core.adaptor.NetworkAdaptorFactory;
import in.succinct.bpp.woocommerce.adaptor.WooCommerceAdaptor;
import in.succinct.bpp.woocommerce.helpers.WooCommerceHelper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;

public class Webhook implements Extension {
    static {
        Registry.instance().registerExtension("in.succinct.bpp.shell.hook",new Webhook());
    }
    @Override
    public void invoke(Object... objects) {
        CommerceAdaptor adaptor = (CommerceAdaptor) objects[0];
        NetworkAdaptor networkAdaptor = (NetworkAdaptor) objects[1];
        Path path = (Path) objects[2];
        if (!(adaptor instanceof WooCommerceAdaptor)) {
            return;
        }
        WooCommerceAdaptor wooCommerceAdaptor = (WooCommerceAdaptor) adaptor;
        try {
            hook(wooCommerceAdaptor, networkAdaptor,path);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    public void hook(WooCommerceAdaptor wooCommerceAdaptor, NetworkAdaptor networkAdaptor, Path path) throws Exception{
        String payload = StringUtil.read(path.getInputStream());
        if (ObjectUtil.equals(path.getHeader("X-WC-Webhook-Topic"),"order.updated")){
            String sign = path.getHeader("X-WC-Webhook-Signature");
            Mac mac =Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    wooCommerceAdaptor.getConfiguration().get("in.succinct.bpp.woocommerce.hmac.key").getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] hmacbytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            if (!ObjectUtil.equals(Crypt.getInstance().toBase64(hmacbytes),sign)){
                throw new RuntimeException("Webhook - Signature failed!!");
            }
            JSONObject wooOrder = (JSONObject) JSONValue.parse(payload);
            WooCommerceHelper helper = wooCommerceAdaptor.getHelper();

            wooOrder = helper.woo_get("/orders/" + wooOrder.get("id"),new JSONObject()); //Need to call to get items array etc.


            final Request request = new Request();
            request.setMessage(new Message());
            request.setContext(new Context());
            request.getMessage().setOrder(helper.getBecknOrder(wooOrder));
            Context context = request.getContext();
            context.setBppId(wooCommerceAdaptor.getSubscriber().getSubscriberId());
            context.setBppUri(wooCommerceAdaptor.getSubscriber().getSubscriberUrl());
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
            networkAdaptor.getApiAdaptor().callback(wooCommerceAdaptor,request);

        }
    }
}
