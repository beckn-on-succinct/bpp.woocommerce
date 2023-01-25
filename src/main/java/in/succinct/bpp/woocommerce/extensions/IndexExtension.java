package in.succinct.bpp.woocommerce.extensions;

import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.db.model.application.Event;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.background.core.DbTask;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.Intent;
import in.succinct.beckn.Message;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Request;
import in.succinct.bpp.core.adaptor.CommerceAdaptor;
import in.succinct.bpp.search.db.model.Item;
import in.succinct.bpp.search.db.model.Provider;
import in.succinct.bpp.woocommerce.adaptor.WooCommerceAdaptor;

public class IndexExtension implements Extension {
    static {
        Registry.instance().registerExtension("in.succinct.bpp.search.extension.index.full",new IndexExtension());
    }

    @Override
    public void invoke(Object... context) {
        CommerceAdaptor adaptor = (CommerceAdaptor) context[0];
        Application application = (Application) context[1];
        indexItems(adaptor,application);
    }

    private void indexItems(CommerceAdaptor adaptor, Application application) {
        if (Database.getTable(Provider.class).recordCount() > 0){
            new Select().from(Item.class).where(new Expression(ModelReflector.instance(Item.class).getPool(),"ACTIVE", Operator.EQ)).execute(Item.class).forEach(i->{
                i.setActive(true);i.save();
            });
            return;
        }
        Request request = new Request();
        request.setMessage(new Message());
        request.getMessage().setIntent(new Intent());

        Request response = new Request();
        ((WooCommerceAdaptor)adaptor)._search(request,response);
        Providers providers = response.getMessage().getCatalog().getProviders();


        TaskManager.instance().executeAsync((DbTask)()->{
            Event event = Event.find(CATALOG_SYNC_EVENT);
            if (event != null ){
                event.raise(providers);
            }
        },false);

    }
    public static String  CATALOG_SYNC_EVENT = "catalog_index";
}
