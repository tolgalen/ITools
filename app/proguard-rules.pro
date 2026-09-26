# BouncyCastle, tomlj, snakeyaml and libphonenumber are used directly (no reflection), so R8 can shrink them
-dontwarn org.bouncycastle.**
-dontwarn org.antlr.**
-dontwarn javax.naming.**
