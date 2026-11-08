# Domácí widget (4×2) – čas, datum, baterie, budík a teplota z MiHome

Widget zobrazuje: čas, datum, nejbližší budík (ze systému, tedy i z hodin Xiaomi),
teplotu + vlhkost + baterii hlavního teploměru a menší teplotu + vlhkost druhého teploměru.

## Jak získat APK (bez Android Studia)
1. Na GitHubu vytvořte nový (klidně soukromý) repozitář.
2. Add file → Upload files → přetáhněte VŠECHNO z rozbalené složky, včetně skryté složky `.github`.
3. Karta Actions → workflow „Build APK“ → počkejte na zelenou fajfku (cca 5 min).
4. Otevřete doběhnutý běh, dole v „Artifacts“ stáhněte `domaci-widget-apk`, rozbalte a
   `app-debug.apk` nainstalujte v telefonu.

## Použití
1. Spusťte aplikaci „Domácí widget“, přihlaste se Xiaomi účtem (region `de`).
2. Přidejte widget 4×2 na plochu a vyberte hlavní a pak druhý teploměr (druhý je volitelný).
   Klepnutím na název hlavního nebo druhého senzoru ho změníte, klepnutím na teplotu obnovíte
   data, klepnutím na čas/budík otevřete budíky.
3. V nastavení telefonu povolte aplikaci automatické spouštění a baterii „Bez omezení“.
