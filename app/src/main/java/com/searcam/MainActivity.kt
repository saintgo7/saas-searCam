package com.searcam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.searcam.ui.navigation.SearCamNavHost
import com.searcam.ui.theme.SearCamTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * SearCam 단일 액티비티 (Single Activity Pattern)
 *
 * 모든 화면 전환은 Jetpack Navigation + NavHost로 처리한다.
 * Activity는 NavHost의 컨테이너 역할만 담당한다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge 레이아웃 활성화 (상태바, 네비게이션바 영역까지 콘텐츠 확장)
        enableEdgeToEdge()

        setContent {
            SearCamTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SearCamNavHost()
                }
            }
        }
    }
}
