-verbose
-allowaccessmodification
-repackageclasses
# We use this app for benchmark purposes
-dontobfuscate

# Using ktor client in Android has missing proguard rule
# See https://youtrack.jetbrains.com/issue/KTOR-5528
-dontwarn org.slf4j.**
