# Clavier Tuner -- R8 rules for the release build.
#
# R8 resolves ServiceLoader lookups at build time and the result is not deterministic unless
# the implementations are kept: F-Droid's reproducible-build guidance gives exactly these two
# kotlinx.coroutines classes as the example (f-droid.org/docs/Reproducible_Builds, read 10 Oct
# 2026). The app uses kotlinx-coroutines in the engine.
-keep class kotlinx.coroutines.CoroutineExceptionHandler
-keep class kotlinx.coroutines.internal.MainDispatcherFactory
