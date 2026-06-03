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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.ico.nekofeed.data.model.UserStats
import com.ico.nekofeed.ui.auth.AuthUiState
import com.ico.nekofeed.ui.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onNavigateToLikes: () -> Unit,
    onNavigateToCollections: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    var userStats by remember { mutableStateOf<UserStats?>(null) }
    var isLoadingStats by remember { mutableStateOf(false) }

    LaunchedEffect(authState.isLoggedIn) {
        if (!authState.isLoggedIn) {
            onLogout()
        }
    }

    ProfileScreenContent(
        authState = authState,
        userStats = userStats,
        isLoadingStats = isLoadingStats,
        onLogout = { authViewModel.logout() },
        onNavigateToLikes = onNavigateToLikes,
        onNavigateToCollections = onNavigateToCollections,
        onNavigateToHistory = onNavigateToHistory,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenContent(
    authState: AuthUiState,
    userStats: UserStats?,
    isLoadingStats: Boolean,
    onLogout: () -> Unit,
    onNavigateToLikes: () -> Unit,
    onNavigateToCollections: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "退出登录")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "头像",
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 用户名
            Text(
                text = authState.user?.username ?: "用户",
                style = MaterialTheme.typography.headlineMedium
            )

            // 简介
            if (!authState.user?.bio.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = authState.user?.bio ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 等级
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "等级: ${authState.user?.level ?: "普通"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 统计卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "数据统计",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 菜单项
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    MenuItem(
                        icon = Icons.Default.Favorite,
                        title = "我的点赞",
                        onClick = onNavigateToLikes
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    MenuItem(
                        icon = Icons.Default.Collections,
                        title = "我的收藏",
                        onClick = onNavigateToCollections
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    MenuItem(
                        icon = Icons.Default.History,
                        title = "浏览历史",
                        onClick = onNavigateToHistory
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    MenuItem(
                        icon = Icons.Default.Edit,
                        title = "编辑资料",
                        onClick = { }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    MenuItem(
                        icon = Icons.Default.Lock,
                        title = "修改密码",
                        onClick = { }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 退出登录按钮
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("退出登录")
            }
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

@Preview(showBackground = true)
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
            onNavigateToLikes = {},
            onNavigateToCollections = {},
            onNavigateToHistory = {},
            onBack = {}
        )
    }
}
