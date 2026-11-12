package com.freetvgarden.abhijeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        val repo = Repo(applicationContext)
        setContent { App(store, repo) }
    }
}
