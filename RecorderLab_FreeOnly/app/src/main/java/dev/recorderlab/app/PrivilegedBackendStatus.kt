package dev.recorderlab.app
object PrivilegedBackendStatus {
 enum class State { NOT_BUNDLED, NOT_AUTHORIZED, NOT_AVAILABLE }
 data class Backend(val id:String,val state:State,val detail:String)
 fun inventory()=listOf(
  Backend("vendor-binder",State.NOT_BUNDLED,"رابط اختصاصی Binder اپ مرجع در این پروژه وجود ندارد."),
  Backend("vendor-daemon",State.NOT_BUNDLED,"Daemon اختصاصی سازنده در این نسخه وجود ندارد."),
  Backend("android-public-api",State.NOT_AUTHORIZED,"Android ممکن است دسترسی ضبط صدای تماس را رد کند.")
 )
}
