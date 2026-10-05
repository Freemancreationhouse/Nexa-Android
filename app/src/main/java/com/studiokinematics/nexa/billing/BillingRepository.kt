package com.studiokinematics.nexa.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.studiokinematics.nexa.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingRepository(context:Context):PurchasesUpdatedListener{
    private val app=context.applicationContext
    private val productId=BuildConfig.NEXA_PLUS_PRODUCT_ID.trim()
    private val _state=MutableStateFlow<NexaPlusState>(NexaPlusState.Loading)
    val state:StateFlow<NexaPlusState> = _state.asStateFlow()
    private var productDetails:ProductDetails?=null

    private val client=BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init{
        if(productId.isBlank())_state.value=NexaPlusState.Unavailable("NEXA+ is not configured for this build.")
        else connect()
    }

    private fun connect(){
        client.startConnection(object:BillingClientStateListener{
            override fun onBillingSetupFinished(result:BillingResult){
                if(result.responseCode==BillingClient.BillingResponseCode.OK){
                    refreshPurchases();queryProduct()
                }else _state.value=NexaPlusState.Unavailable(result.debugMessage.ifBlank{"Google Play Billing is unavailable."})
            }
            override fun onBillingServiceDisconnected(){
                if(_state.value !is NexaPlusState.Active)_state.value=NexaPlusState.Unavailable("Google Play Billing disconnected.")
            }
        })
    }

    private fun queryProduct(){
        if(productId.isBlank()||!client.isReady)return
        val params=QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId).setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        client.queryProductDetailsAsync(params){result,detailsResult->
            if(result.responseCode!=BillingClient.BillingResponseCode.OK){
                if(_state.value !is NexaPlusState.Active)_state.value=NexaPlusState.Error(result.debugMessage.ifBlank{"Could not load NEXA+."})
                return@queryProductDetailsAsync
            }
            val details=detailsResult.productDetailsList.firstOrNull()
            if(details==null){
                if(_state.value !is NexaPlusState.Active)_state.value=NexaPlusState.Unavailable("NEXA+ is not available for this Play account/build.")
                return@queryProductDetailsAsync
            }
            productDetails=details
            val offer=details.subscriptionOfferDetails?.firstOrNull()
            val price=offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice.orEmpty()
            if(offer==null||price.isBlank()){
                if(_state.value !is NexaPlusState.Active)_state.value=NexaPlusState.Unavailable("No eligible NEXA+ offer is available.")
            }else if(_state.value !is NexaPlusState.Active){
                _state.value=NexaPlusState.Available(ProductOffer(details.name,price,offer.offerToken))
            }
        }
    }

    fun launchPurchase(activity:Activity){
        val details=productDetails
        val available=_state.value as? NexaPlusState.Available
        if(details==null||available==null){queryProduct();return}
        val productParams=BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details).setOfferToken(available.offer.offerToken).build()
        val result=client.launchBillingFlow(
            activity,BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
        )
        if(result.responseCode!=BillingClient.BillingResponseCode.OK)
            _state.value=NexaPlusState.Error(result.debugMessage.ifBlank{"Could not open Google Play purchase."})
    }

    fun refreshPurchases(){
        if(!client.isReady)return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ){result,purchases->
            if(result.responseCode==BillingClient.BillingResponseCode.OK)handlePurchases(purchases)
        }
    }

    override fun onPurchasesUpdated(result:BillingResult,purchases:MutableList<Purchase>?){
        when(result.responseCode){
            BillingClient.BillingResponseCode.OK->handlePurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED->if(_state.value !is NexaPlusState.Active)queryProduct()
            else->_state.value=NexaPlusState.Error(result.debugMessage.ifBlank{"Purchase could not be completed."})
        }
    }

    private fun handlePurchases(purchases:List<Purchase>){
        val purchase=purchases.firstOrNull{productId in it.products}
        when(purchase?.purchaseState){
            Purchase.PurchaseState.PURCHASED->{
                _state.value=NexaPlusState.Active
                if(!purchase.isAcknowledged){
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    ){}
                }
            }
            Purchase.PurchaseState.PENDING->_state.value=NexaPlusState.Pending
            else->if(_state.value !is NexaPlusState.Available)queryProduct()
        }
    }

    fun close(){client.endConnection()}
}
