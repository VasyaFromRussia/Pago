package io.octo.bear.pago.model.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;

import com.android.vending.billing.IInAppBillingService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.octo.bear.pago.BillingActivity;
import io.octo.bear.pago.Pago;
import io.octo.bear.pago.model.entity.Purchase;
import io.octo.bear.pago.model.entity.PurchaseType;
import io.octo.bear.pago.model.entity.ResponseCode;
import io.octo.bear.pago.model.entity.Sku;
import io.octo.bear.pago.model.exception.BillingException;

/**
 * Created by shc on 15.07.16.
 */
final class BillingServiceHelper {

    private static final String EXTRA_ITEM_ID_LIST = "ITEM_ID_LIST";

    private static final String RESPONSE_CODE = "RESPONSE_CODE";
    private static final String RESPONSE_DETAILS_LIST = "DETAILS_LIST";
    private static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
    private static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    private static final String RESPONSE_INAPP_DATA_SIGNATURE = "INAPP_DATA_SIGNATURE";

    final List<Sku> getSkuDetails(Context context, List<String> purchaseIds, PurchaseType type) throws RemoteException {
        final Bundle querySku = new Bundle();
        querySku.putStringArrayList(EXTRA_ITEM_ID_LIST, new ArrayList<>(purchaseIds));

        final IInAppBillingService service = getBillingService();

        final Bundle details = service.getSkuDetails(Pago.BILLING_API_VERSION, context.getPackageName(), type.value, querySku);
        final ResponseCode responseCode = ResponseCode.getByCode(details.getInt(RESPONSE_CODE));

        if (responseCode == ResponseCode.OK) {
            final ArrayList<String> skus = details.getStringArrayList(RESPONSE_DETAILS_LIST);
            if (skus == null) throw new RuntimeException("skus list is not supplied");

            final List<Sku> result = new ArrayList<>();
            for (String serializedSku : skus) {
                result.add(Pago.gson().fromJson(serializedSku, Sku.class));
            }

            return result;
        } else {
            throw new BillingException(responseCode);
        }
    }

    final void purchaseItem(
            Context context, String sku, PurchaseType type, PurchaseSuccessListener listener) throws RemoteException {

        final String payload = UUID.randomUUID().toString();
        final IInAppBillingService billingService = getBillingService();

        final Bundle buyIntentBundle = billingService.getBuyIntent(Pago.BILLING_API_VERSION, context.getPackageName(),
                sku, type.value, payload);

        final ResponseCode responseCode = ResponseCode.getByCode(buyIntentBundle.getInt(RESPONSE_CODE));

        if (responseCode != ResponseCode.OK) {
            throw new BillingException(responseCode);
        }

        final PendingIntent buyIntent = buyIntentBundle.getParcelable(RESPONSE_BUY_INTENT);
        if (buyIntent == null) {
            throw new RuntimeException("unable to retrieve buy intent");
        }

        final BillingActivity.PurchaseListener purchaseListener = getPurchaseListener(payload, listener);

        BillingActivity.start(context, purchaseListener, buyIntent.getIntentSender());
    }

    private BillingActivity.PurchaseListener getPurchaseListener(final String payload, final PurchaseSuccessListener listener) {
        return new BillingActivity.PurchaseListener() {
            @Override
            public void onSuccess(Intent result) {
                final ResponseCode code = ResponseCode.getByCode(result.getIntExtra(RESPONSE_CODE, 0));

                if (code == ResponseCode.OK) {
                    final Purchase purchase = Pago.gson().fromJson(result.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA), Purchase.class);
                    final boolean purchaseDataIsCorrect = TextUtils.equals(payload, purchase.developerPayload);

                    if (purchaseDataIsCorrect) {
                        listener.onSuccess(purchase);
                    } else {
                        throw new RuntimeException("purchase data doesn't match with data that was sent in request");
                    }
                } else {
                    throw new BillingException(code);
                }
            }

            @Override
            public void onError() {
                throw new RuntimeException();
            }
        };
    }

    interface PurchaseSuccessListener {
        void onSuccess(Purchase purchase);
    }

}
