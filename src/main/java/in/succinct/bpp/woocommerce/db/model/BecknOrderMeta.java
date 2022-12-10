package in.succinct.bpp.woocommerce.db.model;

import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.model.Model;

public interface BecknOrderMeta extends Model {
    @UNIQUE_KEY("bt")
    @Index
    public String getBecknTransactionId();
    public void setBecknTransactionId(String becknTransactionId);


    @Index
    @UNIQUE_KEY("wo")
    public String getWooCommerceOrderId();
    public void setWooCommerceOrderId(String wooCommerceOrderId);

}
