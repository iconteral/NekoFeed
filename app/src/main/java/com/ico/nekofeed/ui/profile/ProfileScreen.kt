package com.ico.nekofeed.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.ico.nekofeed.data.model.UserStats
import com.ico.nekofeed.ui.auth.AuthUiState
import com.ico.nekofeed.ui.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onLogin: () -> Unit,
    onNavigateToLikes: () -> Unit,
    onNavigateToCollections: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
    loadStats: (suspend () -> UserStats?)? = null
) {
    val authState by authViewModel.uiState.collectAsState()
    var userStats by remember { mutableStateOf<UserStats?>(null) }
    var isLoadingStats by remember { mutableStateOf(false) }

    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn && loadStats != null) {
            isLoadingStats = true
            userStats = loadStats()
            isLoadingStats = false
        }
    }

    ProfileScreenContent(
        authState = authState,
        userStats = userStats,
        isLoadingStats = isLoadingStats,
        onLogout = { 
            authViewModel.logout()
            onLogout()
        },
        onLogin = onLogin,
        onNavigateToLikes = onNavigateToLikes,
        onNavigateToCollections = onNavigateToCollections,
        onNavigateToHistory = onNavigateToHistory,
        onBack = onBack,
        onNavigateToAiSettings = onNavigateToAiSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreenContent(
    authState: AuthUiState,
    userStats: UserStats?,
    isLoadingStats: Boolean,
    onLogout: () -> Unit,
    onLogin: () -> Unit,
    onNavigateToLikes: () -> Unit,
    onNavigateToCollections: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {}
) {
    val isLoggedIn = authState.isLoggedIn

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "个人中心",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (isLoggedIn) {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "退出登录")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 头像与基本信息
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "头像",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isLoggedIn) authState.user?.username ?: "用户" else "访客",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            if (isLoggedIn) {
                if (!authState.user?.bio.isNullOrEmpty()) {
                    Text(
                        text = authState.user?.bio ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Surface(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "等级: ${authState.user?.level ?: "普通"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoggedIn) {
                // 统计数据
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            icon = Icons.Default.Favorite,
                            label = "点赞",
                            count = userStats?.likesCount ?: 0
                        )
                        StatItem(
                            icon = Icons.Default.Collections,
                            label = "收藏",
                            count = userStats?.collectionsCount ?: 0
                        )
                        StatItem(
                            icon = Icons.Default.History,
                            label = "历史",
                            count = userStats?.historyCount ?: 0
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 菜单
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        MenuItem(
                            icon = Icons.Default.Favorite,
                            title = "我的点赞",
                            onClick = onNavigateToLikes
                        )
                        MenuItem(
                            icon = Icons.Default.Collections,
                            title = "我的收藏",
                            onClick = onNavigateToCollections
                        )
                        MenuItem(
                            icon = Icons.Default.History,
                            title = "浏览历史",
                            onClick = onNavigateToHistory
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        MenuItem(
                            icon = Icons.Default.Settings,
                            title = "AI 设置",
                            onClick = onNavigateToAiSettings
                        )
                    }
                }
            } else {
                // 访客模式
                Button(
                    onClick = onLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape
                ) {
                    Text("登录 / 注册", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    MenuItem(
                        icon = Icons.Default.Settings,
                        title = "通用设置",
                        onClick = onNavigateToAiSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    count: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true, name = "已登录")
@Composable
fun ProfileScreenPreview() {
    MaterialTheme {
        ProfileScreenContent(
            authState = AuthUiState(
                isLoggedIn = true,
                user = com.ico.nekofeed.data.model.User(
                    id = 1,
                    username = "NekoUser",
                    avatar = null,
                    bio = "AI 与猫咪爱好者",
                    level = "Premium"
                )
            ),
            userStats = UserStats(
                likesCount = 128,
                collectionsCount = 45,
                historyCount = 320
            ),
            isLoadingStats = false,
            onLogout = {},
            onLogin = {},
            onNavigateToLikes = {},
            onNavigateToCollections = {},
            onNavigateToHistory = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "访客模式")
@Composable
fun ProfileScreenGuestPreview() {
    MaterialTheme {
        ProfileScreenContent(
            authState = AuthUiState(isLoggedIn = false),
            userStats = null,
            isLoadingStats = false,
            onLogout = {},
            onLogin = {},
            onNavigateToLikes = {},
            onNavigateToCollections = {},
            onNavigateToHistory = {},
            onBack = {}
        )
    }
}
