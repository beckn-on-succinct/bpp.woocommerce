package in.succinct.bpp.woocommerce.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.routing.Config;
import in.succinct.bpp.core.db.model.AdaptorCredential;
import in.succinct.json.JSONObjectWrapper;
import org.json.simple.JSONObject;

import java.util.StringTokenizer;

public class CredentialsTemplate implements Extension {
    static {
        Registry.instance().registerExtension("bpp.shell.fill.credentials",new CredentialsTemplate());
    }
    
    @Override
    public void invoke(Object... context) {
        JSONObject template = (JSONObject) context[0];
        AdaptorCredential adaptorCredential = (AdaptorCredential)context[1];
        String adaptorName = new StringTokenizer(Config.instance().getHostName(),".").nextToken();
        
        if (ObjectUtil.equals(adaptorCredential.getAdaptorName(),adaptorName)) {
            String strCredJson = adaptorCredential.getCredentialJson();
            JSONObject credJson = new JSONObject(){{
                put("X-Client-Id","");
                put("X-Client-Secret","");
                put("X-Store-Url","");
                put("X-Webhook-Secret","");
            }};
            if (!ObjectUtil.isVoid(strCredJson)){
                JSONObject o   = JSONObjectWrapper.parse(strCredJson);
                credJson.putAll(o);
            }
            
            template.putAll(credJson);
            // Example Attributes
        }
    }
}
