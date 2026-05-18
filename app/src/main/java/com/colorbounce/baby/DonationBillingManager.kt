package com.colorbounce.baby

import androidx.activity.ComponentActivity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DonationBillingUiState(
    val billingReady: Boolean = false,
    val productDetails: Map<String, ProductDetails> = emptyMap(),
    val purchasingTier: DonationTier? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class DonationBillingManager(
    activity: ComponentActivity
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activityRef = activity

    private val _state = MutableStateFlow(DonationBillingUiState())
    val state: StateFlow<DonationBillingUiState> = _state.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun start() {
        if (billingClient.isReady) {
            refreshProducts()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshProducts()
                    processUnfinishedPurchases()
                } else {
                    _state.update {
                        it.copy(
                            billingReady = false,
                            errorMessage = "Billing is unavailable right now."
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(billingReady = false) }
            }
        })
    }

    fun priceLabel(tier: DonationTier): String {
        val details = _state.value.productDetails[tier.productId]
        return details?.oneTimePurchaseOfferDetails?.formattedPrice ?: tier.fallbackPrice
    }

    fun launchPurchase(tier: DonationTier) {
        if (_state.value.purchasingTier != null) return
        val details = _state.value.productDetails[tier.productId]
        if (details == null) {
            _state.update {
                it.copy(errorMessage = "This option is not available yet. Try again in a moment.")
            }
            if (billingClient.isReady) {
                refreshProducts()
            } else {
                start()
            }
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        _state.update {
            it.copy(
                purchasingTier = tier,
                statusMessage = null,
                errorMessage = null
            )
        }
        val result = billingClient.launchBillingFlow(activityRef, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(
                    purchasingTier = null,
                    errorMessage = billingErrorMessage(result.responseCode)
                )
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update { it.copy(purchasingTier = null) }
            }
            else -> {
                _state.update {
                    it.copy(
                        purchasingTier = null,
                        errorMessage = billingErrorMessage(result.responseCode)
                    )
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun refreshProducts() {
        val productList = DonationTier.productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            scope.launch {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val map = productDetailsList.orEmpty().associateBy { it.productId }
                    _state.update {
                        it.copy(
                            billingReady = true,
                            productDetails = map,
                            errorMessage = if (map.isEmpty()) {
                                "Donation products are not set up in Play Console yet."
                            } else {
                                null
                            }
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            billingReady = billingClient.isReady,
                            errorMessage = "Could not load donation options."
                        )
                    }
                }
            }
        }
    }

    private fun processUnfinishedPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val tier = DonationTier.all.find { purchase.products.contains(it.productId) }
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(consumeParams) { consumeResult, _ ->
            if (consumeResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.update {
                    it.copy(
                        purchasingTier = null,
                        statusMessage = tier?.let { t ->
                            "Thank you for your ${t.label.lowercase()} contribution!"
                        } ?: "Thank you for your support!"
                    )
                }
            } else {
                if (!purchase.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ack) { _ -> }
                }
                _state.update {
                    it.copy(
                        purchasingTier = null,
                        statusMessage = "Thank you! Your support means a lot."
                    )
                }
            }
        }
    }

    private fun billingErrorMessage(code: Int): String = when (code) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play billing is not available on this device."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            "This donation option is not available in your region or account."
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            "Lost connection to Google Play. Please try again."
        else -> "Purchase could not be completed. Please try again."
    }
}
