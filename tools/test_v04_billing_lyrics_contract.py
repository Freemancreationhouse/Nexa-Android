from pathlib import Path
import sys
root=Path(".")
billing=root/"app/src/main/java/com/studiokinematics/nexa/billing"
lyrics=root/"app/src/main/java/com/studiokinematics/nexa/data/LyricsRepository.kt"
states=root/"app/src/main/java/com/studiokinematics/nexa/model/LyricsState.kt"
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")
main=(root/"app/src/main/java/com/studiokinematics/nexa/MainActivity.kt").read_text(encoding="utf-8")
src="\n".join(p.read_text(encoding="utf-8") for p in billing.rglob("*.kt")) if billing.exists() else ""
checks={
    "billing 9.1": "billing-ktx:9.1.0" in gradle,
    "product id build config": "NEXA_PLUS_PRODUCT_ID" in gradle,
    "play price": "formattedPrice" in src and "ProductOffer" in src,
    "pending not active": "NexaPlusState.Pending" in src and "NexaPlusState.Active" in src,
    "purchase acknowledge": "acknowledgePurchase" in src,
    "lyrics states": states.exists() and "Synced" in states.read_text() and "Unavailable" in states.read_text(),
    "lyrics no scraping": lyrics.exists() and "permitted" in lyrics.read_text().lower(),
    "main entitlement wiring": "OfflineEntitlement.NEXA_PLUS" in main and "BillingRepository" in main,
}
failed=[k for k,v in checks.items() if not v]
if failed:
    print("FAIL V0.4 billing/lyrics contract")
    for k in failed: print(" -",k)
    sys.exit(1)
print("PASS V0.4 billing/lyrics contract")
for k in checks: print(" -",k)
