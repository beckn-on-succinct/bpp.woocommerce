package in.succinct.bpp.woocommerce.extensions;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.path.Path;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.Order;
import in.succinct.beckn.Request;
import in.succinct.bpp.core.adaptor.CommerceAdaptor;

import in.succinct.bpp.core.adaptor.api.NetworkApiAdaptor;
import in.succinct.bpp.woocommerce.adaptor.ECommerceAdaptor;
import in.succinct.onet.core.adaptor.NetworkAdaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

public class Webhook implements Extension {
    static {
        Registry.instance().registerExtension("in.succinct.bpp.shell.hook",new Webhook());
    }
    @Override
    public void invoke(Object... objects) {
        CommerceAdaptor adaptor = (CommerceAdaptor) objects[0];
        NetworkAdaptor networkAdaptor = (NetworkAdaptor)objects[1];
        Path path = (Path) objects[2];
        if (!(adaptor instanceof ECommerceAdaptor)) {
            return;
        }
        ECommerceAdaptor eCommerceAdaptor = (ECommerceAdaptor) adaptor;
        try {
            hook(eCommerceAdaptor, networkAdaptor,path);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    public void hook(ECommerceAdaptor eCommerceAdaptor, NetworkAdaptor networkAdaptor,Path path) throws Exception{
        String payload = StringUtil.read(path.getInputStream());
        //Validate auth headers from path.getHeader
        String sign = path.getHeader("X-Hmac-SHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                    eCommerceAdaptor.getConfiguration().get("in.succinct.bpp.woocommerce.hmac.key").getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
        byte[] hmacbytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        if (!ObjectUtil.equals(Crypt.getInstance().toBase64(hmacbytes),sign)){
            throw new RuntimeException("Webhook - Signature failed!!");
        }
           
        if (path.action().equals("order_hook")){
            Order becknOrder = new Order();// fillRest.
            final Request request = new Request();
            request.setMessage(new Message());
            request.setContext(new Context());
            request.getMessage().setOrder(becknOrder); 
            Context context = request.getContext();
            context.setBppId(eCommerceAdaptor.getSubscriber().getSubscriberId());
            context.setBppUri(eCommerceAdaptor.getSubscriber().getSubscriberUrl());
            context.setTimestamp(new Date());
            context.setAction("on_status");
            context.setDomain(eCommerceAdaptor.getSubscriber().getDomain());
            //Fill any other attributes needed.
            //Send unsolicited on_status.
            context.setMessageId(UUID.randomUUID().toString());
            ((NetworkApiAdaptor)networkAdaptor.getApiAdaptor()).callback(eCommerceAdaptor,request);

        }
    }
}
