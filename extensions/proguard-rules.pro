# The extension DEX is merged into a heavily obfuscated host app (Gboard).
# R8's synthetic lambda classes were being renamed to root names such as La;/Lc;.
# Those descriptors already exist in Gboard, so the host class won at runtime and
# the Cafe Activity crashed with NoSuchMethodError on its listener constructor.
# Preserve extension class/package names so every synthetic class remains namespaced.
-dontobfuscate
-keep class app.gboard.cafe.runtime.** { *; }
-keep class app.gboard.cafe.runtime.**$* { *; }
