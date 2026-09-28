package com.memorycapture.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.memorycapture.app.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProBillingState(
    val connected: Boolean = false,
    val loading: Boolean = true,
    val isPro: Boolean = false,
    val priceLabel: String? = null,
    val productAvailable: Boolean = false,
    val pendingPurchase: Boolean = false,
    val message: String? = null,
)

object ProBillingManager : PurchasesUpdatedListener {
    const val PRODUCT_ID = "memorycapture_pro_lifetime"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var applicationContext: Context
    private lateinit var preferences: AppPreferences
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    private val mutableState = MutableStateFlow(ProBillingState())
    val state: StateFlow<ProBillingState> = mutableState.asStateFlow()

    fun initialize(context: Context) {
        if (::applicationContext.isInitialized) return

        applicationContext = context.applicationContext
        preferences = AppPreferences(applicationContext)

        scope.launch {
            val cached = preferences.proEntitlementCached.first()
            mutableState.value = mutableState.value.copy(
                isPro = cached,
                loading = true,
            )
        }

        billingClient = BillingClient.newBuilder(applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .enableAutoServiceReconnection()
            .build()

        connect()
    }

    fun connect() {
        val client = billingClient ?: return
        if (client.isReady) {
            refresh()
            return
        }

        mutableState.value = mutableState.value.copy(loading = true, message = null)

        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingResponseCode.OK) {
                        mutableState.value = mutableState.value.copy(
                            connected = true,
                            loading = true,
                            message = null,
                        )
                        refresh()
                    } else {
                        mutableState.value = mutableState.value.copy(
                            connected = false,
                            loading = false,
                            message = result.debugMessage,
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    mutableState.value = mutableState.value.copy(
                        connected = false,
                        message = "Google Play connection unavailable.",
                    )
                }
            },
        )
    }

    fun refresh() {
        queryProduct()
        queryPurchases()
    }

    fun restorePurchases() {
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        queryPurchases(restoring = true)
    }

    fun launchPurchase(activity: Activity) {
        val client = billingClient
        val details = productDetails

        if (client == null || !client.isReady || details == null) {
            mutableState.value = mutableState.value.copy(
                message = "MemoryCapture Pro is not available from Google Play yet.",
            )
            connect()
            return
        }

        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
        if (offer == null) {
            mutableState.value = mutableState.value.copy(
                message = "No eligible Pro purchase offer is available.",
            )
            return
        }

        val offerToken = offer.offerToken
        if (offerToken.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(
                message = "No valid Google Play offer token is available for Pro.",
            )
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build(),
        )

        if (result.responseCode != BillingResponseCode.OK) {
            mutableState.value = mutableState.value.copy(
                message = result.debugMessage,
            )
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingResponseCode.USER_CANCELED -> {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    message = null,
                )
            }
            else -> {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    message = billingResult.debugMessage,
                )
            }
        }
    }

    private fun queryProduct() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingResponseCode.OK) {
                productDetails = queryResult.productDetailsList.firstOrNull()
                val offer = productDetails
                    ?.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()

                mutableState.value = mutableState.value.copy(
                    loading = false,
                    productAvailable = productDetails != null && offer != null,
                    priceLabel = offer?.formattedPrice,
                    message = if (productDetails == null) {
                        "Create $PRODUCT_ID in Google Play Console to enable purchases."
                    } else {
                        null
                    },
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    productAvailable = false,
                    message = result.debugMessage,
                )
            }
        }
    }

    private fun queryPurchases(restoring: Boolean = false) {
        val client = billingClient ?: return
        if (!client.isReady) {
            connect()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                processPurchases(purchases, restoring)
            } else {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    message = result.debugMessage,
                )
            }
        }
    }

    private fun processPurchases(
        purchases: List<Purchase>,
        restoring: Boolean = false,
    ) {
        val proPurchase = purchases.firstOrNull {
            PRODUCT_ID in it.products
        }

        val purchased = proPurchase?.purchaseState == Purchase.PurchaseState.PURCHASED
        val pending = proPurchase?.purchaseState == Purchase.PurchaseState.PENDING

        if (purchased && proPurchase != null && !proPurchase.isAcknowledged) {
            acknowledge(proPurchase)
        }

        scope.launch {
            preferences.setProEntitlementCached(purchased)
        }

        mutableState.value = mutableState.value.copy(
            loading = false,
            isPro = purchased,
            pendingPurchase = pending,
            message = when {
                pending -> "Your Pro purchase is pending in Google Play."
                restoring && purchased -> "MemoryCapture Pro restored successfully."
                restoring -> "No previous Pro purchase was found."
                else -> null
            },
        )
    }

    private fun acknowledge(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(
                    message = result.debugMessage,
                )
            }
        }
    }

    fun close() {
        billingClient?.endConnection()
        billingClient = null
    }
}
