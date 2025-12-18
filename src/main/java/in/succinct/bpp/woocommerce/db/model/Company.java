package in.succinct.bpp.woocommerce.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;

public interface Company extends com.venky.swf.plugins.collab.db.model.participants.admin.Company {
    @COLUMN_DEF(StandardDefault.BOOLEAN_TRUE)
    boolean isPrepaidSupported();
    void setPrepaidSupported(boolean prepaidSupported);
    
    @COLUMN_DEF(StandardDefault.BOOLEAN_TRUE)
    boolean isPostDeliverySupported();
    void setPostDeliverySupported(boolean prepaidSupported);
    
    
    @COLUMN_DEF(StandardDefault.BOOLEAN_TRUE)
    boolean isStorePickupSupported();
    void setStorePickupSupported(boolean storePickupSupported);
    
    @COLUMN_DEF(StandardDefault.BOOLEAN_TRUE)
    boolean isHomeDeliverySupported();
    void setHomeDeliverySupported(boolean homeDeliverySupported);
    
    @COLUMN_DEF(StandardDefault.ZERO)
    int getMaxDistance();
    void setMaxDistance(int maxDistance);
    
    @IS_NULLABLE
    String getNotesOnDeliveryCharges();
    void setNotesOnDeliveryCharges(String notesOnDeliveryCharges);
    
    String getCompanyOwnerName();
    void setCompanyOwnerName(String companyOwnerName);
    
    boolean isKycOK();
    void setKycOK(boolean kycOk);
    
    String getVerificationStatus();
    void setVerificationStatus(String verificationStatus);
    
    String getNetworkEnvironment();
    void setNetworkEnvironment(String networkEnvironment);
    
    boolean isSuspended();
    void setSuspended(boolean suspended);
}
