package in.succinct.bpp.woocommerce.helpers;

import com.venky.core.util.ObjectUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BecknIdHelper {
    public static String getIdPrefix(){
        return "./local_retail/ind/";
    }

    public enum Entity {
        fulfillment,
        category,
        provider,
        provider_category,
        provider_location,
        item,
        catalog,
        cancellation_reason,
        return_reason,
        order
    }


    public static String getLocalUniqueId(String beckId, Entity becknEntity) {
        String pattern = "^(.*/)(.*)@(.*)\\." + becknEntity + "$";
        Matcher matcher = Pattern.compile(pattern).matcher(beckId);
        if (matcher.find()){
            return matcher.group(2);
        }
        return "-1";
    }
    public static String getBecknId(String localUniqueId, String subscriber_id , Entity becknEntity){
        StringBuilder builder = new StringBuilder();
        builder.append(getIdPrefix());
        if (!ObjectUtil.isVoid(localUniqueId)){
            builder.append(localUniqueId);
        }else {
            builder.append(0);
        }
        builder.append("@");
        builder.append(subscriber_id);
        if (becknEntity != null){
            builder.append(".").append(becknEntity);
        }
        return builder.toString();
    }


}
