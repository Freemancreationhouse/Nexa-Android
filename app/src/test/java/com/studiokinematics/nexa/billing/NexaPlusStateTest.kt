package com.studiokinematics.nexa.billing

import org.junit.Assert.*
import org.junit.Test

class NexaPlusStateTest {
    @Test fun availableOfferKeepsGoogleFormattedPrice() {
        val state=NexaPlusState.Available(ProductOffer("NEXA+","₹provider-price","token"))
        assertEquals("₹provider-price",state.offer.price)
    }
    @Test fun pendingPurchaseIsNotActiveEntitlement() {
        val state:NexaPlusState=NexaPlusState.Pending
        assertFalse(state is NexaPlusState.Active)
    }
}
