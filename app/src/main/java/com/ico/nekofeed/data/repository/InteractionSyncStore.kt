package com.ico.nekofeed.data.repository

import com.ico.nekofeed.data.model.ItemInteraction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// ============================================================================
// 【数据层 · 事件总线（Event Bus）】
// ============================================================================
//
// 📌 问题：详情页点赞后，怎么通知首页也更新？
//    - 方案 A：首页每次显示时重新请求服务端（慢，浪费流量）
//    - 方案 B：用事件总线实时广播（快，省内存）← 本项目采用
//
// 📌 SharedFlow vs StateFlow：
//    - StateFlow:   有初始值，始终有"当前值"，适合持有 UI 状态
//    - SharedFlow:  无初始值，只在有新事件时通知订阅者，适合事件广播
//
// 📌 关键参数：
//    - replay = 0:              新订阅者不会收到历史事件（只收未来的）
//    - extraBufferCapacity = 32: 缓冲区大小，tryEmit 不会因为无人订阅而丢失
//
// 📌 object 关键字：Kotlin 的单例模式，全局只有一个 InteractionSyncStore 实例
// ====================================================================

/** 互动状态更新事件 */
data class ItemInteractionUpdate(
    val itemId: String,
    val interaction: ItemInteraction
)

/**
 * InteractionSyncStore —— 互动状态同步总线
 *
 * 工作流程：
 *    1. 用户在详情页点赞 → ViewModel 调用 publish(itemId, interaction)
 *    2. InteractionSyncStore 广播事件给所有订阅者
 *    3. 首页的 ViewModel 收到事件 → 更新 allItems → UI 自动刷新
 *
 * 使用 object 声明为全局单例，所有页面共享同一个实例。
 */
object InteractionSyncStore {
    // MutableSharedFlow: 可写的事件流（内部用）
    // replay = 0: 新订阅者不会收到历史事件
    // extraBufferCapacity = 32: 缓冲 32 个事件，防止 emit 时无人接收导致挂起
    private val _updates = MutableSharedFlow<ItemInteractionUpdate>(
        replay = 0,
        extraBufferCapacity = 32
    )

    // SharedFlow: 只读的事件流（对外暴露），外部只能订阅，不能发送
    val updates: SharedFlow<ItemInteractionUpdate> = _updates.asSharedFlow()

    /**
     * 发布互动状态更新
     *
     * tryEmit: 非挂起版本的 emit，缓冲区满时返回 false 而不是挂起
     * 在本项目中，互动事件不会频繁到打满 32 的缓冲区
     */
    fun publish(itemId: String, interaction: ItemInteraction) {
        _updates.tryEmit(ItemInteractionUpdate(itemId, interaction))
    }
}
