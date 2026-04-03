package com.searcam.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import com.searcam.data.local.AppDatabase
import com.searcam.data.local.dao.ChecklistDao
import com.searcam.data.local.dao.DeviceDao
import com.searcam.data.local.dao.ReportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Singleton

/**
 * Room 데이터베이스 DI 모듈
 *
 * AppDatabase 싱글톤 인스턴스와 각 DAO를 제공한다.
 * DAO는 DB 인스턴스에서 직접 파생되므로 별도 싱글톤 선언 불필요.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Room AppDatabase 싱글톤 — SQLCipher 암호화 적용
     *
     * Android Keystore에서 AES-256 키를 생성/조회하여 DB 암호화 패스프레이즈로 사용한다.
     * 루팅 기기 또는 adb 백업으로도 평문 데이터 접근이 불가능하다.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val passphrase = getDatabasePassphrase()
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "searcam.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Android Keystore에서 DB 암호화 키를 가져온다.
     * 키가 없으면 새로 생성하여 Keystore에 안전하게 저장한다.
     */
    private fun getDatabasePassphrase(): ByteArray {
        val keyAlias = "searcam_db_key"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        val secretKey: SecretKey = if (keyStore.containsAlias(keyAlias)) {
            (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            Timber.d("DB 암호화 키 신규 생성")
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }
        return secretKey.encoded
    }

    /**
     * 스캔 리포트 DAO
     */
    @Provides
    fun provideReportDao(database: AppDatabase): ReportDao = database.reportDao()

    /**
     * 탐지된 네트워크 기기 DAO
     */
    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()

    /**
     * 체크리스트 DAO
     */
    @Provides
    fun provideChecklistDao(database: AppDatabase): ChecklistDao = database.checklistDao()
}
