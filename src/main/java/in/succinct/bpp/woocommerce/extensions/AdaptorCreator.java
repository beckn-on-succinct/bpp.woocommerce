package in.succinct.bpp.woocommerce.extensions;

import com.venky.core.util.ObjectHolder;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import in.succinct.bpp.core.adaptor.CommerceAdaptor;
import in.succinct.bpp.core.adaptor.NetworkAdaptor;
import in.succinct.bpp.woocommerce.adaptor.WooCommerceAdaptor;

import java.util.Map;

public class AdaptorCreator implements Extension {
    static {
        Registry.instance().registerExtension(CommerceAdaptor.class.getName(),new AdaptorCreator());
    }
    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Object... context) {
        Map<String,String> properties = (Map<String,String>) context[0];
        Subscriber subscriber = (Subscriber) context[1];
        ObjectHolder<CommerceAdaptor> commerceAdaptorHolder = (ObjectHolder<CommerceAdaptor>) context[3];
        if (properties.containsKey("in.succinct.bpp.woocommerce.storeUrl")){
            commerceAdaptorHolder.set(new WooCommerceAdaptor(properties,subscriber));
        }
    }

}
