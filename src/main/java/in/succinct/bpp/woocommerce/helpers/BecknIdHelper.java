package in.succinct.bpp.woocommerce.helpers;

import com.venky.core.util.ObjectUtil;
import in.succinct.beckn.Context;
import in.succinct.bpp.shell.util.BecknUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BecknIdHelper {
    public static String getIdPrefix(){
        return "./local_retail/ind/";
    }

    public static String getIdSuffix(){
        return BecknUtil.getSubscriberId();
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

    public static String getBecknId(Long localUniqueId,Entity becknEntity){
        return getBecknId(String.valueOf(localUniqueId),becknEntity);
    }
    public static String getBecknId(String localUniqueId,Entity becknEntity){
        return getBecknId(getIdPrefix(),localUniqueId, getIdSuffix(), becknEntity);
    }
    public static String getLocalUniqueId(String beckId, Entity becknEntity) {
        String pattern = "^(.*/)(.*)@(.*)\\." + becknEntity + "$";
        Matcher matcher = Pattern.compile(pattern).matcher(beckId);
        if (matcher.find()){
            return matcher.group(2);
        }
        return "-1";
    }
    public static String getBecknId(String prefix, String localUniqueId, String suffix , Entity becknEntity){
        StringBuilder builder = new StringBuilder();
        builder.append(prefix);
        if (!ObjectUtil.isVoid(localUniqueId)){
            builder.append(localUniqueId);
        }else {
            builder.append(0);
        }
        builder.append("@");
        builder.append(suffix);
        if (becknEntity != null){
            builder.append(".").append(becknEntity);
        }
        return builder.toString();
    }


}
