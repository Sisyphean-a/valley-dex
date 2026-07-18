package com.example.stardewoffline.testsupport

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stardewoffline.core.common.HashUtils
import com.example.stardewoffline.core.database.content.ContentDatabaseFactory
import com.example.stardewoffline.core.database.content.ContentDatabaseManager
import com.example.stardewoffline.core.database.user.UserDatabase
import com.example.stardewoffline.core.datapackage.DataPackageInstaller
import com.example.stardewoffline.core.datapackage.DataPackageManager
import com.example.stardewoffline.core.datapackage.DataPackageValidator
import com.example.stardewoffline.core.datapackage.SafeZipExtractor
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.DataPackageRepository
import com.example.stardewoffline.data.SearchRepository
import com.example.stardewoffline.data.UserDataRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

class TestAppScenario private constructor(
    val context: Context,
    val dataPackages: DataPackageManager,
    val packageRepository: DataPackageRepository,
    val contentRepository: ContentRepository,
    val searchRepository: SearchRepository,
    val userRepository: UserDataRepository,
    val preferences: AppPreferencesRepository,
    private val contentDatabases: ContentDatabaseManager,
    private val userDatabase: UserDatabase,
    private val workspace: File,
) {
    val viewModels = TestViewModelStoreOwner()

    suspend fun close() {
        viewModels.clear()
        contentDatabases.close()
        userDatabase.close()
        preferences.update(::clearPreferences)
        workspace.deleteRecursively()
    }

    companion object {
        suspend fun create(context: Context): TestAppScenario {
            val testContext = TestApplicationContext(context)
            val preferences = AppPreferencesRepository(testContext)
            preferences.update(::clearPreferences)
            File(testContext.filesDir, "content").deleteRecursively()
            val factory = ContentDatabaseFactory(Dispatchers.IO)
            val validator = DataPackageValidator(testJson, HashUtils(Dispatchers.IO), factory, Dispatchers.IO)
            val databases = ContentDatabaseManager(testContext, preferences, factory, Dispatchers.IO)
            val manager = DataPackageManager(
                testContext,
                DataPackageInstaller(testContext, SafeZipExtractor(Dispatchers.IO), validator, Dispatchers.IO),
                validator,
                preferences,
                databases,
            )
            val userDatabase = Room.inMemoryDatabaseBuilder(testContext, UserDatabase::class.java).build()
            return TestAppScenario(
                context = testContext,
                dataPackages = manager,
                packageRepository = DataPackageRepository(testContext, manager),
                contentRepository = ContentRepository(databases),
                searchRepository = SearchRepository(databases),
                userRepository = UserDataRepository(userDatabase.userDataDao()),
                preferences = preferences,
                contentDatabases = databases,
                userDatabase = userDatabase,
                workspace = testContext.filesDir,
            )
        }

        private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = false }

        @Suppress("UNCHECKED_CAST")
        private fun clearPreferences(values: MutablePreferences) {
            values.asMap().keys.forEach { key -> values.remove(key as Preferences.Key<Any>) }
        }
    }
}

private class TestApplicationContext(base: Context) : ContextWrapper(base) {
    private val workspace = File(base.filesDir, "instrumentation-${UUID.randomUUID()}").apply {
        check(mkdirs()) { "无法创建测试目录: $absolutePath" }
    }

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = workspace
}

fun instrumentationTestContext(): Context {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    return TestRuntimeContext(instrumentation.context, instrumentation.targetContext)
}

private class TestRuntimeContext(
    private val testAssetsContext: Context,
    storageContext: Context,
) : ContextWrapper(storageContext) {
    override fun getApplicationContext(): Context = this
    override fun getAssets() = testAssetsContext.assets
}

class TestViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun clear() = viewModelStore.clear()
}

class TestViewModelFactory(
    private val creators: Map<Class<out ViewModel>, () -> ViewModel>,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = requireNotNull(creators[modelClass]) { "缺少 ${modelClass.simpleName} 的测试 ViewModel" }
        @Suppress("UNCHECKED_CAST")
        return creator() as T
    }
}
