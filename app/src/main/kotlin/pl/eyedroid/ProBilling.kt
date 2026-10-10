package pl.eyedroid

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Play Console prerequisite: create and activate a one-time INAPP product "eyedroid_pro".
 * Local client-side entitlement is suitable for beta, not fraud-resistant release.
 */
class ProBilling(private val app: Application) : PurchasesUpdatedListener {
    val entitled = MutableStateFlow(false)
    val available = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var details: ProductDetails? = null
    private val client = BillingClient.newBuilder(app).setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        ).build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() { available.value = false }
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    message.value = "Google Play Billing unavailable / Płatności Google Play niedostępne"
                    return
                }
                refresh()
                val query = QueryProductDetailsParams.newBuilder()
                    .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("eyedroid_pro")
                        .setProductType(BillingClient.ProductType.INAPP).build())).build()
                client.queryProductDetailsAsync(query) { billingResult, productResult ->
                    details = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                        productResult.productDetailsList.firstOrNull() else null
                    available.value = details != null
                }
            }
        })
    }

    fun refresh() {
        if (!client.isReady) return
        val query = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(query) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val valid = purchases.filter { it.products.contains("eyedroid_pro") &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED }
                entitled.value = valid.isNotEmpty()
                StreamState.pro.value = entitled.value
                valid.forEach { acknowledge(it) }
            }
        }
    }

    fun buy(activity: Activity) {
        val product = details ?: run {
            message.value = "Pro unavailable: configure eyedroid_pro in Play Console / Skonfiguruj produkt w Google Play"
            return
        }
        val offer = product.oneTimePurchaseOfferDetails ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .setOfferToken(offer.offerToken ?: "").build())).build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.filter { it.products.contains("eyedroid_pro") &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { acknowledge(it) }
            refresh()
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            message.value = result.debugMessage
        }
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken).build()) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK)
                message.value = result.debugMessage
        }
    }
    fun close() { client.endConnection() }
}
