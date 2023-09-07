package in.succinct.bpp.woocommerce.extensions;

import com.venky.core.util.ObjectHolder;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import in.succinct.bpp.core.adaptor.CommerceAdaptor;

import in.succinct.bpp.woocommerce.adaptor.ECommerceAdaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class AdaptorCreator implements Extension {
    static {
        Registry.instance().registerExtension(CommerceAdaptor.class.getName(),new AdaptorCreator());
    }
    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Object... context) {
        Map<String,String> properties = (Map<String,String>) context[0];
        Subscriber subscriber = (Subscriber) context[1];
        ObjectHolder<CommerceAdaptor> commerceAdaptorHolder = (ObjectHolder<CommerceAdaptor>) context[2];
        if (properties.containsKey("in.succinct.bpp.woocommerce.storeUrl")){
           commerceAdaptorHolder.set(getAdaptor(properties,subscriber));
        }
    }
    private final Map<String, ECommerceAdaptor> map = new HashMap<>();
    ECommerceAdaptor getAdaptor(Map<String,String> config,Subscriber subscriber){
        Map<String, String> sortedMap = new TreeMap<>(config);
        String key = String.format("%s|%s", sortedMap,subscriber.getSubscriberUrl());
        ECommerceAdaptor adaptor = map.get(key);
        if (adaptor != null){
            return adaptor;
        }

        synchronized (map){
            adaptor = map.get(key);
            if (adaptor == null){
                adaptor = new ECommerceAdaptor(sortedMap,subscriber);
                map.put(key,adaptor);
            }
            return adaptor;
        }
    }
}
