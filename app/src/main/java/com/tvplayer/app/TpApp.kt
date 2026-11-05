package com.tvplayer.app

import android.app.Application
import com.tvplayer.app.data.AppContainer

class TpApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
